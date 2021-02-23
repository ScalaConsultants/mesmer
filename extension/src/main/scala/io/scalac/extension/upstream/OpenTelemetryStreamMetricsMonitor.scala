package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.StreamMetricsMonitor
import io.scalac.extension.upstream.OpenTelemetryStreamMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry.{ WrappedCounter, WrappedLongValueRecorder }

object OpenTelemetryStreamMetricsMonitor {
  case class MetricNames(operatorProcessed: String, runningStreams: String)

  object MetricNames {
    private val defaults: MetricNames = MetricNames("akka_streams_operator_processed", "akka_streams_running_streams")

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._

      config.tryValue("io.scalac.akka-monitoring.metrics.streams-metrics")(_.getConfig).map { streamMetricsConfig =>
        val operatorProcessed = streamMetricsConfig
          .tryValue("operator-processed")(_.getString)
          .getOrElse(defaults.operatorProcessed)

        val runningStreams = streamMetricsConfig
          .tryValue("running-streams")(_.getString)
          .getOrElse(defaults.runningStreams)

        MetricNames(operatorProcessed, runningStreams)
      }
    }.getOrElse(defaults)
  }

  def apply(instrumentationName: String, config: Config): OpenTelemetryStreamMetricsMonitor =
    new OpenTelemetryStreamMetricsMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryStreamMetricsMonitor(instrumentationName: String, metricNames: MetricNames)
    extends StreamMetricsMonitor {

  import StreamMetricsMonitor._

  private val meter = OpenTelemetry
    .getGlobalMeter(instrumentationName)

  private val operatorProcessed = meter
    .longCounterBuilder(metricNames.operatorProcessed)
    .setDescription("Amount of messages process by operator")
    .build()

  private val runningStreamsRecorder = meter
    .longValueRecorderBuilder(metricNames.runningStreams)
    .setDescription("Amount of running streams")
    .build()

  override def bind(labels: Labels): BoundMonitor = new StreamMetricsBoundMonitor(labels)

  class StreamMetricsBoundMonitor(labels: Labels) extends BoundMonitor {
    private val openTelementryLabels = labels.toOpenTelemetry

    override val operatorProcessedMessages = WrappedCounter(operatorProcessed, openTelementryLabels)

    override val runningStreams =
      WrappedLongValueRecorder(runningStreamsRecorder, openTelementryLabels)

    override def unbind(): Unit = {
      runningStreams.unbind()
      operatorProcessedMessages.unbind()
    }
  }
}
