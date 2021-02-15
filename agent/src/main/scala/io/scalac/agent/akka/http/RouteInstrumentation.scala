package io.scalac.agent.akka.http

import java.lang.reflect.Method

import scala.concurrent.{ ExecutionContextExecutor, Future }

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server._
import akka.http.scaladsl.settings.{ ParserSettings, RoutingSettings }
import akka.stream.{ Materializer, SystemMaterializer }

import net.bytebuddy.implementation.bind.annotation.{ SuperMethod, This }

import io.scalac.agent.util.FunctionOps._

class RouteInstrumentation
object RouteInstrumentation {

  def asyncHandler(
    route: Route,
    routingSettings: RoutingSettings,
    parserSettings: ParserSettings,
    materializer: Materializer,
    routingLog: RoutingLog,
    executionContext: ExecutionContextExecutor,
    rejectionHandler: RejectionHandler,
    exceptionHandler: ExceptionHandler,
    @SuperMethod method: Method,
    @This self: Any
  ): HttpRequest => Future[HttpResponse] = {

    // this is not ideal - java reflections are not well optimized by JIT

    materializer.asInstanceOf[SystemMaterializer]
    method
      .invoke(
        self,
        route.latency(millis => println(s"Request took ${millis} millis"))(
          executionContext
        ),
        routingSettings,
        parserSettings,
        materializer,
        routingLog,
        executionContext,
        rejectionHandler,
        exceptionHandler
      )
      .asInstanceOf[HttpRequest => Future[HttpResponse]]
  }
}
