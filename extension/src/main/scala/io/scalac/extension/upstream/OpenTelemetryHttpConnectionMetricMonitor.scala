package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry

import io.scalac.extension.metric.HttpConnectionMetricMonitor
import io.scalac.extension.metric.RegisterRoot
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
  def apply(instrumentationName: String, config: Config): OpenTelemetryHttpConnectionMetricMonitor =
    new OpenTelemetryHttpConnectionMetricMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryHttpConnectionMetricMonitor(
  instrumentationName: String,
  metricNames: MetricNames
) extends HttpConnectionMetricMonitor {

  import HttpConnectionMetricMonitor._

  private val meter = OpenTelemetry
    .getGlobalMeter(instrumentationName)

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

    override val connectionCounter: WrappedUpDownCounter =
      upDownCounter(connectionTotalCounter, openTelemetryLabels).register(this)

  }
}
