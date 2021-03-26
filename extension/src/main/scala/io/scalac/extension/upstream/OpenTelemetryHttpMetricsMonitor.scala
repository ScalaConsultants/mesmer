package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.{ HttpMetricMonitor, RegisterRoot }
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryHttpMetricsMonitor {
  case class MetricNames(
    connectionTotal: String,
    requestDuration: String,
    requestTotal: String
  )

  object MetricNames {
    def default: MetricNames =
      MetricNames(
        "connection_total",
        "request_duration",
        "request_total"
      )

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

          val requestDuration = clusterMetricsConfig
            .tryValue("request-duration")(_.getString)
            .getOrElse(defaultCached.requestDuration)

          val requestTotal = clusterMetricsConfig
            .tryValue("request-total")(_.getString)
            .getOrElse(defaultCached.requestTotal)

          MetricNames(connectionTotal, requestDuration, requestTotal)
        }
        .getOrElse(defaultCached)
    }
  }
  def apply(instrumentationName: String, config: Config): OpenTelemetryHttpMetricsMonitor =
    new OpenTelemetryHttpMetricsMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryHttpMetricsMonitor(
  instrumentationName: String,
  metricNames: OpenTelemetryHttpMetricsMonitor.MetricNames
) extends HttpMetricMonitor {

  import HttpMetricMonitor._

  private val meter = OpenTelemetry
    .getGlobalMeter(instrumentationName)

  private val connectionTotalCounter = meter
    .longUpDownCounterBuilder(metricNames.connectionTotal)
    .setDescription("Amount of connections")
    .build()

  private val requestTimeRequest = meter
    .longValueRecorderBuilder(metricNames.requestDuration)
    .setDescription("Amount of ms request took to complete")
    .build()

  private val requestTotalCounter = meter
    .longCounterBuilder(metricNames.requestTotal)
    .setDescription("Amount of requests")
    .build()

  def bind(labels: Labels): BoundMonitor = new HttpMetricsBoundMonitor(labels)

  class HttpMetricsBoundMonitor(labels: Labels)
      extends opentelemetry.Synchronized(meter)
      with BoundMonitor
      with SynchronousInstrumentFactory
      with RegisterRoot {
    private val openTelemetryLabels = LabelsFactory.of(labels.serialize)

    override val connectionCounter = upDownCounter(connectionTotalCounter, openTelemetryLabels).register(this)

    override val requestTime = metricRecorder(requestTimeRequest, openTelemetryLabels).register(this)

    override val requestCounter = counter(requestTotalCounter, openTelemetryLabels).register(this)

  }
}
