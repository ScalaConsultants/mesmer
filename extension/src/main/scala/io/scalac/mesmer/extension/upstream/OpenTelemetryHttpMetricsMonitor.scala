package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter
import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.extension.metric.{ HttpMetricsMonitor, RegisterRoot }
import io.scalac.mesmer.extension.upstream.OpenTelemetryHttpMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryHttpMetricsMonitor {
  final case class MetricNames(
    requestDuration: String,
    requestTotal: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] {

    protected val mesmerConfig: String = "metrics.http-metrics"

    protected val defaultConfig: MetricNames = MetricNames(
      "akka_http_request_duration",
      "akka_http_request_total"
    )

    protected def extractFromConfig(config: Config): MetricNames = {
      val requestDuration = config
        .tryValue("request-duration")(_.getString)
        .getOrElse(defaultConfig.requestDuration)

      val requestTotal = config
        .tryValue("request-total")(_.getString)
        .getOrElse(defaultConfig.requestTotal)

      MetricNames(requestDuration, requestTotal)
    }

  }
  def apply(
    meter: Meter,
    moduleConfig: AkkaHttpModule.AkkaHttpRequestMetricsDef[Boolean],
    config: Config
  ): OpenTelemetryHttpMetricsMonitor =
    new OpenTelemetryHttpMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))
}

final class OpenTelemetryHttpMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaHttpModule.AkkaHttpRequestMetricsDef[Boolean],
  metricNames: MetricNames
) extends HttpMetricsMonitor {

  import HttpMetricsMonitor._

  private lazy val requestTimeRequest = meter
    .longValueRecorderBuilder(metricNames.requestDuration)
    .setDescription("Amount of ms request took to complete")
    .build()

  private lazy val requestTotalCounter = meter
    .longCounterBuilder(metricNames.requestTotal)
    .setDescription("Amount of requests")
    .build()

  def bind(labels: Labels): BoundMonitor = new HttpMetricsBoundMonitor(labels)

  final class HttpMetricsBoundMonitor(labels: Labels)
      extends opentelemetry.Synchronized(meter)
      with BoundMonitor
      with SynchronousInstrumentFactory
      with RegisterRoot {

    protected val otLabels = LabelsFactory.of(labels.serialize)

    val requestTime =
      if (moduleConfig.requestTime) metricRecorder(requestTimeRequest, otLabels).register(this)
      else noopMetricRecorder[Long]

    val requestCounter: WrappedCounter =
      if (moduleConfig.requestCounter) counter(requestTotalCounter, otLabels).register(this) else noopCounter[Long]

  }
}
