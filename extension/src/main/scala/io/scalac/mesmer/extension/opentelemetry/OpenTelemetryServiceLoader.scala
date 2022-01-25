package io.scalac.mesmer.extension.opentelemetry

import java.util.ServiceLoader

import scala.jdk.CollectionConverters._

trait OpenTelemetryServiceLoader {
  def load(): Iterator[OpenTelemetryProvider]
}

object OpenTelemetryServiceLoader {
  implicit val otelProviderLoader: OpenTelemetryServiceLoader = () =>
    ServiceLoader.load(classOf[OpenTelemetryProvider]).iterator().asScala

}
