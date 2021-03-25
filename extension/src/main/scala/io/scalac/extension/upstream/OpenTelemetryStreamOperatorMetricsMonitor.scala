package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.StreamOperatorMetricsMonitor.{ BoundMonitor, Labels }
import io.scalac.extension.metric.{ MetricObserver, RegisterRoot, StreamOperatorMetricsMonitor }
import io.scalac.extension.upstream.OpenTelemetryStreamOperatorMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryStreamOperatorMetricsMonitor {
  case class MetricNames(operatorProcessed: String, connections: String, runningOperators: String)

  object MetricNames {
    private val defaults: MetricNames =
      MetricNames(
        "akka_streams_operator_processed_total",
        "akka_streams_operator_connections",
        "akka_streams_running_operators"
      )

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._

      config.tryValue("io.scalac.akka-monitoring.metrics.streams-metrics")(_.getConfig).map { streamMetricsConfig =>
        val operatorProcessed = streamMetricsConfig
          .tryValue("operator-processed")(_.getString)
          .getOrElse(defaults.operatorProcessed)

        val operatorConnections = streamMetricsConfig
          .tryValue("operator-connections")(_.getString)
          .getOrElse(defaults.connections)

        val runningOperators = streamMetricsConfig
          .tryValue("running-operators")(_.getString)
          .getOrElse(defaults.runningOperators)

        MetricNames(operatorProcessed, operatorConnections, runningOperators)
      }
    }.getOrElse(defaults)
  }

  def apply(instrumentationName: String, config: Config): OpenTelemetryStreamOperatorMetricsMonitor =
    new OpenTelemetryStreamOperatorMetricsMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryStreamOperatorMetricsMonitor(instrumentationName: String, metricNames: MetricNames)
    extends StreamOperatorMetricsMonitor {

  private val meter = OpenTelemetry
    .getGlobalMeter(instrumentationName)

  private val processedMessageAdapter = new LongSumObserverBuilderAdapter[Labels](
    meter
      .longSumObserverBuilder(metricNames.operatorProcessed)
      .setDescription("Amount of messages process by operator")
  )

  private val operatorsAdapter = new LongMetricObserverBuilderAdapter[Labels](
    meter
      .longValueObserverBuilder(metricNames.runningOperators)
      .setDescription("Amount of operators in a system")
  )

  override def bind(): StreamOperatorMetricsMonitor.BoundMonitor = new BoundMonitor with RegisterRoot {

    override lazy val processedMessages: MetricObserver[Long, Labels] =
      processedMessageAdapter.createObserver(this)

    override lazy val operators: MetricObserver[Long, Labels] = operatorsAdapter.createObserver(this)

  }
}
