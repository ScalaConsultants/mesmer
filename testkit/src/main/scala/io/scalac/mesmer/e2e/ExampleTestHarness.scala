package io.scalac.mesmer.e2e

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Paths

import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.ExposedService
import io.circe.Json
import io.circe.parser._
import org.scalatest.Suite

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.sys.process.{ Process => ScalaProcess }
import scala.sys.process.{ ProcessLogger => ScalaProcessLogger }
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

trait ExampleTestHarness { this: Suite =>

  private val sbtExecutable = if (sys.props("os.name").toLowerCase.contains("win")) {
    "cmd" :: "/c" :: "sbt" :: Nil
  } else {
    "sbt" :: Nil
  }

  private val (projectRoot, dockerComposeFile) = {
    // sbt shell needs `./`, IntelliJ run/debug configurations need `../../`
    val maybeProjectRoots = Set(new File("../../"), new File("./"))
    val constructDockerComposePath = (projectRoot: File) =>
      Paths.get(projectRoot.toString, "examples/docker/docker-compose.yaml").toFile
    val projectRoot = maybeProjectRoots
      .find(maybeProjectRoot => constructDockerComposePath(maybeProjectRoot).exists())
      .getOrElse(sys.error("Project root not found"))
    (projectRoot, constructDockerComposePath(projectRoot))
  }

  private val containerDef = DockerComposeContainer.Def(
    dockerComposeFile,
    exposedServices = Seq(
      ExposedService("prometheus", 9090)
    )
  )

  protected def withExample(sbtCommand: String, startTestString: String = "Example started")(
    block: DockerComposeContainer => Unit
  ): Unit = {
    val container = containerDef.createContainer()
    container.start()

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
        Await.result(processHandlePromise.future, 60.seconds)
      catch {
        case NonFatal(ex) =>
          fail("failed to start example application", ex)
      }
      block(container)
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

      container.stop()
    }
  }

  def prometheusApiRequest(container: DockerComposeContainer)(
    query: String,
    block: Json => Unit
  ): Unit = {
    val urlString = s"http://localhost:${container.getServicePort("prometheus", 9090)}/api/v1/query?query=$query"
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
