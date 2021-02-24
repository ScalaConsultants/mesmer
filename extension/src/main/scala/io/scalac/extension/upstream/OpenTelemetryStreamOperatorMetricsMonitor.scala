package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.StreamOperatorMetricsMonitor
import io.scalac.extension.upstream.OpenTelemetryStreamOperatorMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry.{ WrappedCounter, WrappedLongValueRecorder, WrappedUpDownCounter }

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

  import StreamOperatorMetricsMonitor._

  private val meter = OpenTelemetry
    .getGlobalMeter(instrumentationName)

  private val operatorProcessed = meter
    .longCounterBuilder(metricNames.operatorProcessed)
    .setDescription("Amount of messages process by operator")
    .build()

  private val operatorConnectionsGauge = meter
    .longUpDownCounterBuilder(metricNames.connections)
    .setDescription("Amount of connections in operator")
    .build()

  private val runningOperatorsRecorder = meter
    .longValueRecorderBuilder(metricNames.runningOperators)
    .setDescription("Amount of running operators")
    .build()

  override def bind(labels: Labels): BoundMonitor = new StreamOperatorMetricsBoundMonitor(labels)

  class StreamOperatorMetricsBoundMonitor(labels: Labels) extends BoundMonitor {
    private val openTelemetryLabels = labels.toOpenTelemetry

    override val processedMessages = WrappedCounter(operatorProcessed, openTelemetryLabels)

    override val connections =
      WrappedUpDownCounter(operatorConnectionsGauge, openTelemetryLabels)

    override val operators = WrappedLongValueRecorder(runningOperatorsRecorder, openTelemetryLabels)

    override def unbind(): Unit = {
      connections.unbind()
      processedMessages.unbind()
      operators.unbind()
    }
  }
}
