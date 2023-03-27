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
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.util.Success
import scala.util.control.NonFatal

trait ExampleTestHarness { this: Suite =>

  // we do this for Windows instead of running in cmd shell to eliminate the need to deal with orphan processes once main process is destroyed
  // TODO can throw "server already running" exceptions, needs a solution
  private val sbtExecutable = if (sys.props("os.name").toLowerCase.contains("win")) {
    Process("cmd" :: "/c" :: "where" :: "sbt" :: Nil).lazyLines.headOption
      .getOrElse(sys.error("sbt executable not found"))
  } else {
    "sbt"
  }

  // TODO needs to work both in sbt shell and IntelliJ run/debug configurations
  private val projectRoot = new File("./")

  private val containerDef = DockerComposeContainer.Def(
    Paths.get(projectRoot.toString, "examples/docker/docker-compose.yaml").toFile,
    exposedServices = Seq(
      ExposedService("prometheus", 9090)
    )
  )

  protected def withExample(sbtCommand: String, startTestString: String = "Example started")(
    block: DockerComposeContainer => Unit
  ): Unit = {
    val process =
      Process(
        sbtExecutable :: sbtCommand :: Nil,
        projectRoot
      )

    val container = containerDef.createContainer()
    container.start()

    val processHandlePromise = Promise[Unit]()
    val processHandle = process.run(
      ProcessLogger(
        line => {
          if (line.contains(startTestString)) {
            processHandlePromise.complete(Success(()))
          }
          sys.process.stdout.println(line)
        },
        sys.process.stderr.println(_)
      )
    )

    try {
      try
        Await.result(processHandlePromise.future, 60.seconds)
      catch {
        case NonFatal(ex) =>
          fail("failed to start example application", ex)
      }
      block(container)
    } finally {
      processHandle.destroy()
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
