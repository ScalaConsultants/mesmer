package io.scalac.mesmer.agent.akka

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.model.headers.Connection
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent
import io.scalac.mesmer.agent.akka.impl.AkkaHttpTestImpl
import io.scalac.mesmer.agent.utils.InstallModule
import io.scalac.mesmer.core.event.HttpEvent.{ConnectionCompleted, ConnectionStarted, RequestCompleted, RequestStarted}
import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.core.util.TestOps
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class AkkaHttpAgentTest
    extends InstallModule(AkkaHttpAgent)
    with AnyFlatSpecLike
    with Matchers
    with OptionValues
    with TestOps {

//  override var agent = Some(AkkaHttpAgent.agent(AkkaHttpModule.defaultConfig))

  behavior of "AkkaHttpAgent"

  it should behave like withVersion(
//    AkkaHttpModule.AkkaHttpModuleConfig(true, true, true),
//    AkkaHttpModule.AkkaHttpModuleConfig(false, true, true),
    AkkaHttpModule.AkkaHttpModuleConfig(true, false, true)
  )(
    "instrument routes to generate events on http requests"
  )(AkkaHttpTestImpl.systemWithHttpService { implicit system => monitor =>
    val testRoute: Route = path("test") {
      get {
        complete((StatusCodes.OK, collection.immutable.Seq(Connection("close"))))
      }
    }
//    implicit val timeout = RouteTestTimeout(5 seconds)
    import system.executionContext


    val response = for {
      binding <- Http().newServerAt("127.0.0.1", 0).bind(testRoute)
      port      = binding.localAddress.getPort
      targetUri = Uri.Empty.withPath(Path("/test")).withHost("127.0.0.1").withPort(port).withScheme("http")
      response <- Http().singleRequest(HttpRequest(HttpMethods.GET, targetUri))
    } yield {
      binding.unbind()
      response
    }
    Await.result(response, 5.seconds)

    monitor.expectMessageType[ConnectionStarted]
    monitor.expectMessageType[RequestStarted]
    monitor.expectMessageType[RequestCompleted]
    monitor.expectMessageType[ConnectionCompleted]
  })

//  "AkkaHttpAgent" should "instrument routes to generate events on http requests" in withVersion(
//    AkkaHttpModule.defaultConfig
//  )(test { monitor =>
//    implicit val timeout = RouteTestTimeout(5 seconds)
//
//    Get("/test") ~!> testRoute ~> check {
//      status should be(StatusCodes.OK)
//    }
//    monitor.expectMessageType[ConnectionStarted]
//    monitor.expectMessageType[RequestStarted]
//    monitor.expectMessageType[RequestCompleted]
//    monitor.expectMessageType[ConnectionCompleted]
//  })

//  it should "contain 2 transformations" in  test { _ =>
//    agent.value.instrumentations should have size (2)
//  }

  it should behave like withVersion(AkkaHttpModule.defaultConfig)("contain 2 transformations")(AkkaHttpTestImpl.systemWithHttpService {
    _ => _ =>
      agent.value.instrumentations should have size (2)

  })

}
