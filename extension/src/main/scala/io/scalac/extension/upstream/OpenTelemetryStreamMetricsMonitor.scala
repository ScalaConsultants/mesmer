package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.StreamMetricMonitor
import io.scalac.extension.upstream.OpenTelemetryStreamMetricMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry.WrappedLongValueRecorder

object OpenTelemetryStreamMetricMonitor {
  case class MetricNames(runningStreams: String)

  object MetricNames {
    private val defaults: MetricNames =
      MetricNames("akka_streams_running_streams")

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._

      config.tryValue("io.scalac.akka-monitoring.metrics.streams-metrics")(_.getConfig).map { streamMetricsConfig =>
        val runningStreams = streamMetricsConfig
          .tryValue("running-streams")(_.getString)
          .getOrElse(defaults.runningStreams)

        MetricNames(runningStreams)
      }
    }.getOrElse(defaults)
  }
}

class OpenTelemetryStreamMetricMonitor(instrumentationName: String, metricNames: MetricNames)
    extends StreamMetricMonitor {

  import StreamMetricMonitor._

  private val meter = OpenTelemetry
    .getGlobalMeter(instrumentationName)

  private val runningStreamsRecorder = meter
    .longValueRecorderBuilder(metricNames.runningStreams)
    .setDescription("Amount of running streams on a system")
    .build()

  override def bind(labels: Labels): BoundMonitor = new StreamMetricsBoundMonitor(labels)

  class StreamMetricsBoundMonitor(labels: Labels) extends BoundMonitor {
    private val openTelemetryLabels = labels.toOpenTelemetry

    override val runningStreams =
      WrappedLongValueRecorder(runningStreamsRecorder, openTelemetryLabels)

    override def unbind(): Unit =
      runningStreams.unbind()
  }
}
