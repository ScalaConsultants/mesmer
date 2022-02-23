package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.common
import io.opentelemetry.api.metrics.Meter
import io.scalac.mesmer.core.config.{Configuration, MesmerConfiguration}
import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.extension.metric.HttpConnectionMetricsMonitor
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.metric.UpDownCounter
import io.scalac.mesmer.extension.upstream.OpenTelemetryHttpConnectionMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryHttpConnectionMetricsMonitor {
  final case class MetricNames(connectionTotal: String)

  object MetricNames extends MesmerConfiguration[MetricNames] with Configuration  {
    val defaultConfig: MetricNames =
      MetricNames("akka_http_connections")

    protected val mesmerConfig: String = "metrics.http-metrics"

    protected def extractFromConfig(config: Config): MetricNames = {

      val connectionTotal = config
        .tryValue("connections")(_.getString)
        .getOrElse(defaultConfig.connectionTotal)

      MetricNames(connectionTotal)
    }

  }

  def apply(
    meter: Meter,
    moduleConfig: AkkaHttpModule.AkkaHttpConnectionsMetricsDef[Boolean],
    config: Config
  ): OpenTelemetryHttpConnectionMetricsMonitor =
    new OpenTelemetryHttpConnectionMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))
}

final class OpenTelemetryHttpConnectionMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaHttpModule.AkkaHttpConnectionsMetricsDef[Boolean],
  metricNames: MetricNames
) extends HttpConnectionMetricsMonitor {

  import HttpConnectionMetricsMonitor._

  private lazy val connectionTotalCounter = meter
    .upDownCounterBuilder(metricNames.connectionTotal)
    .setDescription("Amount of connections")
    .build()

  def bind(attributes: Attributes): BoundMonitor = new HttpConnectionMetricBoundMonitor(attributes)

  class HttpConnectionMetricBoundMonitor(attributes: Attributes)
      extends opentelemetry.Synchronized(meter)
      with BoundMonitor
      with SynchronousInstrumentFactory
      with RegisterRoot {

    protected lazy val otAttributes: common.Attributes = AttributesFactory.of(attributes.serialize)

    lazy val connections: UpDownCounter[Long] with Instrument[Long] =
      if (moduleConfig.connections)
        upDownCounter(connectionTotalCounter, otAttributes)
      else
        noopUpDownCounter
  }
}
