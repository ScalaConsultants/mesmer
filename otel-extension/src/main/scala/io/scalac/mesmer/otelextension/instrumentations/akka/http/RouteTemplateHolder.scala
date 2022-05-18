package io.scalac.mesmer.otelextension.instrumentations.akka.http

import java.util.concurrent.atomic.AtomicReference

final class RouteTemplateHolder() {
  private val template = new AtomicReference[String]("")

  def append(value: String): String = template.getAndUpdate(_ + value)
  def set(value: String): Unit      = template.set(value)
  def get(): String                 = template.get()
}
