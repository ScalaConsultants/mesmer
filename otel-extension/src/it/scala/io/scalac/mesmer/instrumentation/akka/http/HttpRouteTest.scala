package io.scalac.mesmer.instrumentation.akka.http

import akka.http.scaladsl.model.StatusCodes
import io.scalac.mesmer.agent.utils.OtelAgentTest
import io.scalac.mesmer.core.util.ReceptionistOps
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import io.scalac.mesmer.otelextension.instrumentations.akka.http.RouteContext

import scala.concurrent.Promise
import scala.concurrent.duration._

class HttpRouteTest
    extends OtelAgentTest
    with AnyFlatSpecLike
    with Matchers
    with ScalaFutures
    with ScalatestRouteTest
    with OptionValues
    with ReceptionistOps {

  implicit val timeout: Timeout = 5.seconds

  it should ("add a uuid template") in {
    val promise = Promise[String]()

    val route: Route = (pathPrefix("api" / "v1" / JavaUUID) & pathEndOrSingleSlash) { _ =>
      val template = RouteContext.retrieveFromCurrent.get()
      promise.success(template)
      complete(StatusCodes.OK)

    }

    Get("/api/v1/84a5c573-4643-4bb5-a50d-776a8fca0a5b") ~!> route ~> check {
      status should be(StatusCodes.OK)
    }

    whenReady(promise.future) { template =>
      template should be("/api/v1/<uuid>")
    }
  }

  it should ("add a number template") in {
    val promise = Promise[String]()

    val route: Route = (pathPrefix("api" / "v1" / IntNumber) & pathEndOrSingleSlash) { _ =>
      val template = RouteContext.retrieveFromCurrent.get()
      promise.success(template)
      complete(StatusCodes.OK)

    }

    Get("/api/v1/100") ~!> route ~> check {
      status should be(StatusCodes.OK)
    }

    whenReady(promise.future) { template =>
      template should be("/api/v1/<number>")
    }
  }

  it should ("add a wildcard for segment template") in {
    val promise = Promise[String]()

    val route: Route = (pathPrefix("api" / "v1" / Segment) & pathEndOrSingleSlash) { _ =>
      val template = RouteContext.retrieveFromCurrent.get()
      promise.success(template)
      complete(StatusCodes.OK)

    }

    Get("/api/v1/anu-thing") ~!> route ~> check {
      status should be(StatusCodes.OK)
    }

    whenReady(promise.future) { template =>
      template should be("/api/v1/*")
    }
  }

  it should ("add a uuid template for or matcher") in {
    val promise = Promise[String]()

    val route: Route = (pathPrefix("api" / "v1" / (JavaUUID | IntNumber)) & pathEndOrSingleSlash) { _ =>
      val template = RouteContext.retrieveFromCurrent.get()
      promise.success(template)
      complete(StatusCodes.OK)

    }

    Get("/api/v1/84a5c573-4643-4bb5-a50d-776a8fca0a5b") ~!> route ~> check {
      status should be(StatusCodes.OK)
    }

    whenReady(promise.future) { template =>
      template should be("/api/v1/<uuid>")
    }
  }

  it should ("add a number template for or matcher") in {
    val promise = Promise[String]()

    val route: Route = (pathPrefix("api" / "v1" / (JavaUUID | LongNumber)) & pathEndOrSingleSlash) { _ =>
      val template = RouteContext.retrieveFromCurrent.get()
      promise.success(template)
      complete(StatusCodes.OK)

    }

    Get("/api/v1/100") ~!> route ~> check {
      status should be(StatusCodes.OK)
    }

    whenReady(promise.future) { template =>
      template should be("/api/v1/<number>")
    }
  }

}
