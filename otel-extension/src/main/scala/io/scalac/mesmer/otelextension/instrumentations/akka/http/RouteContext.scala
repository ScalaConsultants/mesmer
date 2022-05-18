package io.scalac.mesmer.otelextension.instrumentations.akka.http

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey

object RouteContext {
  val routeKey: ContextKey[RouteTemplateHolder] =
    ContextKey.named[RouteTemplateHolder]("mesmer-akka-http-route-template")

  def retrieveFromCurrent: RouteTemplateHolder = Context.current().get(routeKey)
}
