package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.{Meter, common}
import io.scalac.mesmer.extension.metric.{HttpConnectionMetricsMonitor, Metric, RegisterRoot, UpDownCounter}
import io.scalac.mesmer.extension.upstream.opentelemetry._
import OpenTelemetryHttpConnectionMetricsMonitor.MetricNames
import io.scalac.mesmer.core.config.MesmerConfiguration

object OpenTelemetryHttpConnectionMetricsMonitor {
  final case class MetricNames(connectionTotal: String)

  object MetricNames extends MesmerConfiguration[MetricNames] {
    protected val defaultConfig: MetricNames =
      MetricNames("akka_http_connections")

     protected val mesmerConfig: String = "metrics.http-metrics"


     protected def extractFromConfig(config: Config): MetricNames = {

       val connectionTotal = config
         .tryValue("connections")(_.getString)
         .getOrElse(defaultConfig.connectionTotal)

       MetricNames(connectionTotal)
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

    protected val otLabels = LabelsFactory.of(labels.serialize)

    val connections: UpDownCounter[Long] with Instrument[Long] =
      upDownCounter(connectionTotalCounter, otLabels).register(this)


//    override def connections: Metric[Long] = ???
  }
}
