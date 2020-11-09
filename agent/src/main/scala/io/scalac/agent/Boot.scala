package io.scalac.agent

import java.lang.instrument.Instrumentation
import java.util.concurrent.Callable

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.{
  ExceptionHandler,
  RejectionHandler,
  Route,
  RoutingLog
}
import akka.http.scaladsl.settings.{ParserSettings, RoutingSettings}
import akka.stream.Materializer
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.SuperCall
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util._

class GreeterStub // required for java/scala compatibility
object GreeterStub {

  def asyncHandler(
    route: Route,
    routingSettings: RoutingSettings,
    parserSettings: ParserSettings,
    materializer: Materializer,
    routingLog: RoutingLog,
    executionContext: ExecutionContextExecutor,
    rejectionHandler: RejectionHandler,
    exceptionHandler: ExceptionHandler,
    @SuperCall call: Callable[HttpRequest => Future[HttpResponse]]
  ): HttpRequest => Future[HttpResponse] = {
    val result = call.call()
    result.andThen(_.andThen {
      case Success(value) => println("Successfuly captured")
    }(executionContext))
  }
}

object Boot {

  def premain(args: String, instrumentation: Instrumentation): Unit = {

    new AgentBuilder.Default()
      .`with`(
        AgentBuilder.Listener.StreamWriting.toSystemOut
          .withTransformationsOnly()
      )
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)
      .`type`(
        ElementMatchers.nameEndsWithIgnoreCase[TypeDescription](
          "akka.http.scaladsl.server.Route$"
        )
      )
      .transform { (builder, typeDescription, classLoader, module) =>
        builder
          .method(
            (named[MethodDescription]("asyncHandler")
              .and(isMethod[MethodDescription])
              .and(not(isAbstract[MethodDescription])))
          )
          .intercept(MethodDelegation.to(classOf[GreeterStub]))
      }
      .installOn(instrumentation)
  }
}
