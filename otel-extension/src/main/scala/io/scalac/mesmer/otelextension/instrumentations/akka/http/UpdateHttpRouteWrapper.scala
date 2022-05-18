package io.scalac.mesmer.otelextension.instrumentations.akka.http

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Using

final class UpdateHttpRouteWrapper(inner: HttpRequest => Future[HttpResponse])(implicit
  executionContext: ExecutionContext
) extends (HttpRequest => Future[HttpResponse]) {
  def apply(v1: HttpRequest): Future[HttpResponse] = {

    val wrapper = new RouteTemplateHolder
    val newContext = Context
      .current()
      .`with`(RouteContext.routeKey, wrapper)

    Using.resource(newContext.makeCurrent()) { _ =>
      inner(v1).andThen { case _ =>
        val template = wrapper.get()
        HttpRouteHolder.updateHttpRoute(newContext, HttpRouteSource.CONTROLLER, template)
      }
    }

  }

}
