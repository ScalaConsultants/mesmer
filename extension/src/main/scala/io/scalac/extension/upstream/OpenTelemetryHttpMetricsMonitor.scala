package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.HttpMetricMonitor
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryHttpMetricsMonitor {
  case class MetricNames(
    requestDuration: String,
    requestTotal: String
  )

  object MetricNames {
    def default: MetricNames =
      MetricNames(
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
          val requestDuration = clusterMetricsConfig
            .tryValue("request-duration")(_.getString)
            .getOrElse(defaultCached.requestDuration)

          val requestTotal = clusterMetricsConfig
            .tryValue("request-total")(_.getString)
            .getOrElse(defaultCached.requestTotal)

          MetricNames(requestDuration, requestTotal)
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

  private val requestTimeRequest = meter
    .longValueRecorderBuilder(metricNames.requestDuration)
    .setDescription("Amount of ms request took to complete")
    .build()

  private val requestTotalCounter = meter
    .longCounterBuilder(metricNames.requestTotal)
    .setDescription("Amount of requests")
    .build()

  def bind(labels: Labels): BoundMonitor = new HttpMetricsBoundMonitor(labels)

  class HttpMetricsBoundMonitor(labels: Labels) extends BoundMonitor with opentelemetry.Synchronized {
    private val openTelemetryLabels = labels.toOpenTelemetry

    override val requestTime = WrappedLongValueRecorder(requestTimeRequest, openTelemetryLabels)

    override val requestCounter = WrappedCounter(requestTotalCounter, openTelemetryLabels)

    override def unbind(): Unit = {
      requestTime.unbind()
      requestCounter.unbind()
    }
  }
}
