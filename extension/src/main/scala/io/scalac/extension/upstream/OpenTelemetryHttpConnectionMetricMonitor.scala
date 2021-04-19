package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.extension.metric.HttpConnectionMetricMonitor
import io.scalac.extension.metric.RegisterRoot
import io.scalac.extension.metric.UpDownCounter
import io.scalac.extension.upstream.opentelemetry._

import OpenTelemetryHttpConnectionMetricMonitor.MetricNames

object OpenTelemetryHttpConnectionMetricMonitor {
  case class MetricNames(connectionTotal: String)

  object MetricNames {
    def default: MetricNames =
      MetricNames("connection_total")

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._
      val defaultCached = default

      config
        .tryValue("io.scalac.akka-monitoring.metrics.http-metrics")(
          _.getConfig
        )
        .map { clusterMetricsConfig =>
          val connectionTotal = clusterMetricsConfig
            .tryValue("connection-total")(_.getString)
            .getOrElse(defaultCached.connectionTotal)
          MetricNames(connectionTotal)
        }
        .getOrElse(defaultCached)
    }
  }
  def apply(meter: Meter, config: Config): OpenTelemetryHttpConnectionMetricMonitor =
    new OpenTelemetryHttpConnectionMetricMonitor(meter, MetricNames.fromConfig(config))
}

class OpenTelemetryHttpConnectionMetricMonitor(meter: Meter, metricNames: MetricNames)
    extends HttpConnectionMetricMonitor {

  import HttpConnectionMetricMonitor._

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
