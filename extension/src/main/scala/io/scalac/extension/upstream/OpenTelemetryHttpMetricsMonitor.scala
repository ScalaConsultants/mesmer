package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.OpenTelemetry
import io.scalac.extension.metric.Metric._
import io.scalac.extension.metric.{HttpMetricMonitor, MetricRecorder, UpCounter}

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
        .tryValue("io.scalac.akka-cluster-monitoring.http-metrics")(
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

  private val requestTimeRequest = OpenTelemetry
    .getMeter(instrumentationName)
    .longValueRecorderBuilder(metricNames.requestDuration)
    .setDescription("Amount of ms request took to complete")
    .build()

  private val requestUpDownCounter = OpenTelemetry
    .getMeter(instrumentationName)
    .longCounterBuilder(metricNames.requestTotal)
    .setDescription("Amount of requests")
    .build()

  override def bind(labels: Labels): HttpMetricMonitor.BoundMonitor = new BoundMonitor {
    override val requestTime: MetricRecorder[Long] =
      requestTimeRequest.bind(labels.toOpenTelemetry).toMetricRecorder()

    override val requestCounter: UpCounter[Long] =
      requestUpDownCounter.bind(labels.toOpenTelemetry).toUpCounter()
  }
}
