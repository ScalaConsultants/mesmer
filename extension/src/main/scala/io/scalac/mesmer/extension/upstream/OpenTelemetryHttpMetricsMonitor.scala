package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter
import io.scalac.mesmer.core.config.MesmerConfiguration
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
  def apply(meter: Meter, config: Config): OpenTelemetryHttpMetricsMonitor =
    new OpenTelemetryHttpMetricsMonitor(meter, MetricNames.fromConfig(config))
}

class OpenTelemetryHttpMetricsMonitor(meter: Meter, metricNames: MetricNames) extends HttpMetricsMonitor {

  import HttpMetricsMonitor._

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

    val requestTime: WrappedLongValueRecorder =
      metricRecorder(requestTimeRequest, openTelemetryLabels).register(this)

    val requestCounter: WrappedCounter = counter(requestTotalCounter, openTelemetryLabels).register(this)

  }
}
