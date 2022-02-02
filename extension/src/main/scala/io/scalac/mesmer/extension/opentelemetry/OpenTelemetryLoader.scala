package io.scalac.mesmer.extension.opentelemetry

trait OpenTelemetryLoader {
  def load(): OpenTelemetryProvider
}

object OpenTelemetryLoader {

  implicit val otelLoader: OpenTelemetryLoader = make(implicitly[OpenTelemetryServiceLoader])

  def make(serviceLoader: OpenTelemetryServiceLoader): OpenTelemetryLoader = new OpenTelemetryLoader {

    override def load(): OpenTelemetryProvider = {
      val loadedProviders: Iterator[OpenTelemetryProvider] = serviceLoader.load()

      if (!loadedProviders.hasNext) {
        throw OpenTelemetryConfigurationException("No OpenTelemetry configuration found.")
      } else {
        val provider: OpenTelemetryProvider = loadedProviders.next()

        if (loadedProviders.hasNext) {
          throw OpenTelemetryConfigurationException(
            s"Ambiguous Open Telemetry configuration - found more than one OpenTelemetryProvider instances"
          )
        }
        provider
      }
    }
  }
  private[opentelemetry] case class OpenTelemetryConfigurationException(message: String)
      extends RuntimeException(message)
}
