package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.extension.metric.HttpConnectionMetricsMonitor
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.metric.UpDownCounter
import io.scalac.mesmer.extension.upstream.opentelemetry._

import OpenTelemetryHttpConnectionMetricsMonitor.MetricNames

object OpenTelemetryHttpConnectionMetricsMonitor {
  case class MetricNames(connectionTotal: String)

  object MetricNames {
    def default: MetricNames =
      MetricNames("akka_http_connections")

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.mesmer.extension.config.ConfigurationUtils._
      val defaultCached = default

      config
        .tryValue("io.scalac.akka-monitoring.metrics.http-metrics")(
          _.getConfig
        )
        .map { clusterMetricsConfig =>
          val connectionTotal = clusterMetricsConfig
            .tryValue("connections")(_.getString)
            .getOrElse(defaultCached.connectionTotal)
          MetricNames(connectionTotal)
        }
        .getOrElse(defaultCached)
    }
  }
  def apply(meter: Meter, config: Config): OpenTelemetryHttpConnectionMetricsMonitor =
    new OpenTelemetryHttpConnectionMetricsMonitor(meter, MetricNames.fromConfig(config))
}

class OpenTelemetryHttpConnectionMetricsMonitor(meter: Meter, metricNames: MetricNames)
    extends HttpConnectionMetricsMonitor {

  import HttpConnectionMetricsMonitor._

  private val connectionTotalCounter = meter
    .longUpDownCounterBuilder(metricNames.connectionTotal)
    .setDescription("Amount of connections")
    .build()

  def bind(labels: Labels): BoundMonitor = new HttpConnectionMetricBoundMonitor(labels)

  class HttpConnectionMetricBoundMonitor(labels: Labels)
      extends opentelemetry.Synchronized(meter)
      with BoundMonitor
      with SynchronousInstrumentFactory
      with RegisterRoot {
    private val openTelemetryLabels = LabelsFactory.of(labels.serialize)

    val connectionCounter: UpDownCounter[Long] with Instrument[Long] =
      upDownCounter(connectionTotalCounter, openTelemetryLabels).register(this)

  }
}
