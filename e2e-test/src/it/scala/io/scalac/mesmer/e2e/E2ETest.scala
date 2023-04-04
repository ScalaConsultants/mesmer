package io.scalac.mesmer.e2e

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.ExposedService
import io.circe.Json
import io.circe.parser._
import io.circe.yaml._
import io.circe.yaml.syntax._
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import org.scalatest.Suite
import org.scalatest.TryValues
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.slf4j.LoggerFactory
import org.testcontainers.shaded.org.apache.commons.io.FileUtils

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.sys.process.{ Process => ScalaProcess }
import scala.sys.process.{ ProcessLogger => ScalaProcessLogger }
import scala.util.Failure
import scala.util.Success
import scala.util.Using
import scala.util.control.NonFatal

trait E2ETest extends PatienceConfiguration with EitherValues with TryValues with OptionValues {
  this: Suite =>
  import E2ETest._

  private val isCI = sys.env.contains("CI")

  private val isWindows = sys.props("os.name").toLowerCase.contains("win")

  private val sbtExecutable = if (isWindows) {
    "cmd" :: "/c" :: "sbt" :: Nil
  } else {
    "sbt" :: Nil
  }

  private val (projectRoot, dockerComposeFile) = {
    // sbt shell needs `./`, IntelliJ run/debug configurations need `../../`
    val maybeProjectRoots = Set(new File("../"), new File("./"))
    val constructDockerComposePath = (projectRoot: File) =>
      Paths.get(projectRoot.getAbsolutePath, "examples/docker/docker-compose.yaml").toFile
    val projectRoot = maybeProjectRoots
      .find(maybeProjectRoot => constructDockerComposePath(maybeProjectRoot).exists())
      .getOrElse(sys.error("Project root not found"))

    // remove `container_name` attribute from docker-compose services, because testcontainers do not support it,
    // copy `examples/docker` to a temp directory where we apply the docker-compose transformation
    val dockerComposeJson = Using(Source.fromFile(constructDockerComposePath(projectRoot))) { source =>
      parser.parse(source.reader()).value
    }.success.value.hcursor
      .downField("services")
      .withFocus(
        _.withObject(services =>
          Json.obj(
            services.keys.map { serviceKey =>
              (
                serviceKey,
                services(serviceKey).value.mapObject(_.filterKeys(_ != "container_name"))
              )
            }.toSeq: _*
          )
        )
      )
      .top
      .value
      .asYaml
      .spaces2
    val tmpDockerDirectory = Files.createTempDirectory("mesmer-").toFile
    FileUtils.copyDirectory(Paths.get(projectRoot.toString, "examples/docker").toFile, tmpDockerDirectory)
    val tmpDockerCompose = Paths.get(tmpDockerDirectory.getAbsolutePath, "docker-compose.yaml").toFile
    Files.write(tmpDockerCompose.toPath, dockerComposeJson.getBytes(StandardCharsets.UTF_8))
    (projectRoot, tmpDockerCompose)
  }

  private val containerDef = DockerComposeContainer.Def(
    dockerComposeFile,
    exposedServices = Seq(
      ExposedService("prometheus", 9090)
    )
  )

  implicit override def patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(60, Seconds)),
    interval = scaled(Span(150, Millis))
  )

  protected def withExample(sbtCommand: String, startTestString: String = "Example started")(
    prometheusApiBlock: PrometheusApi => Unit
  ): Unit = {
    val sbtProcessFn = (block: () => Unit) => {
      val processHandlePromise = Promise[Unit]()
      val sbtOptions = List(
        "-Dsbt.server.forcestart=true" // necessary for execution from sbt shell, because multiple sbt servers will be needed
      )
      val process =
        ScalaProcess(
          sbtExecutable ::: sbtOptions ::: sbtCommand :: Nil,
          projectRoot
        )
      val processHandle = process.run(
        ScalaProcessLogger(
          line => {
            if (line.contains(startTestString)) {
              processHandlePromise.complete(Success(()))
            }
            sys.process.stdout.println(line)
          },
          sys.process.stderr.println(_)
        )
      )
      Future {
        blocking {
          val exitValue = processHandle.exitValue()
          if (!processHandlePromise.isCompleted) {
            processHandlePromise.complete(
              if (exitValue != 0)
                Failure(new RuntimeException(s"sbt process exited with a non-zero exit code $exitValue"))
              else Success(())
            )
          }
        }
      }

      try {
        try
          Await.result(processHandlePromise.future, 120.seconds)
        catch {
          case NonFatal(ex) =>
            fail("failed to start example application", ex)
        }
        block()
      } finally {
        // process destruction is more involved, because on Windows orphan processes are not automatically terminated
        val method = processHandle.getClass.getDeclaredField("p")
        method.setAccessible(true)
        val javaProcess = method.get(processHandle).asInstanceOf[java.lang.Process]
        val destroy = (javaProcess: java.lang.Process) => {
          javaProcess
            .descendants()
            .iterator()
            .asScala
            .foreach(_.destroy())
          javaProcess.destroy()
        }
        destroy(javaProcess)
      }
    }

    // cannot run on GitHub Windows CI runners - docker-compose not compatible with Windows containers,
    // so only check if example project manages to start up successfully
    if (isCI && isWindows) {
      sbtProcessFn(() => ())
    } else {
      val container = containerDef.createContainer()
      container.start()

      try
        sbtProcessFn(() => prometheusApiBlock(new PrometheusApi(container)))
      finally
        container.stop()
    }
  }
}

object E2ETest {

  class PrometheusApi(container: DockerComposeContainer) {

    private val logger = LoggerFactory.getLogger(getClass)

    val prometheusPort = {
      val port = container.getServicePort("prometheus", 9090)
      logger.info(s"Prometheus service bound to http://localhost:$port")
      port
    }

    def assert(
      query: String,
      block: Json => Unit
    ): Unit = {
      val urlString = s"http://localhost:$prometheusPort/api/v1/query?query=$query"
      val request = HttpRequest
        .newBuilder()
        .uri(URI.create(urlString))
        .build()
      val client = HttpClient
        .newBuilder()
        .build()
      val response = client.send(request, BodyHandlers.ofString())
      parse(response.body())
        .fold(
          ex => sys.error(s"failed parsing response [${response.body()}] to JSON, error $ex"),
          json => block(json)
        )
    }
  }
}
