package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.common
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.extension.metric.Counter
import io.scalac.mesmer.extension.metric.Histogram
import io.scalac.mesmer.extension.metric.HttpMetricsMonitor
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.upstream.OpenTelemetryHttpMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryHttpMetricsMonitor {
  final case class MetricNames(
    requestDuration: String,
    requestTotal: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] {

    protected val mesmerConfig: String = "metrics.http-metrics"

    val defaultConfig: MetricNames = MetricNames(
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
    .histogramBuilder(metricNames.requestDuration)
    .ofLongs()
    .setDescription("Amount of ms request took to complete")
    .build()

  private lazy val requestTotalCounter = meter
    .counterBuilder(metricNames.requestTotal)
    .setDescription("Amount of requests")
    .build()

  def bind(attributes: Attributes): BoundMonitor = new HttpMetricsBoundMonitor(attributes)

  final class HttpMetricsBoundMonitor(attributes: Attributes)
      extends opentelemetry.Synchronized(meter)
      with BoundMonitor
      with SynchronousInstrumentFactory
      with RegisterRoot {

    protected val otAttributes: common.Attributes = AttributesFactory.of(attributes.serialize)

    lazy val requestTime: Histogram[Long] with Instrument[Long] =
      if (moduleConfig.requestTime) histogram(requestTimeRequest, otAttributes)
      else noopHistogram[Long]

    lazy val requestCounter: Counter[Long] with Instrument[Long] =
      if (moduleConfig.requestCounter) counter(requestTotalCounter, otAttributes) else noopCounter[Long]

  }
}
