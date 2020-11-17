package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.common.Labels
import io.scalac.extension.metric.Metric._
import io.scalac.extension.metric.{ Counter, HttpMetricMonitor, MetricRecorder }
import io.scalac.extension.model._

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
  import HttpMetricMonitor.BoundMonitor

  private val requestTimeRequest = OpenTelemetry
    .getMeter(instrumentationName)
    .longValueRecorderBuilder(metricNames.requestDuration)
    .setDescription("Amount of ms request took to complete")
    .build()

  private val requestUpDownCounter = OpenTelemetry
    .getMeter(instrumentationName)
    .longUpDownCounterBuilder(metricNames.requestTotal)
    .setDescription("Amount of requests")
    .build()

  override def bind(path: Path, method: Method): HttpMetricMonitor.BoundMonitor = new BoundMonitor {
    override val requestTime: MetricRecorder[Long] =
      requestTimeRequest.bind(Labels.of("path", path, "method", method)).toMetricRecorder()

    override val requestCounter: Counter[Long] =
      requestUpDownCounter.bind(Labels.of("path", path, "method", method)).toCounter()
  }
}
