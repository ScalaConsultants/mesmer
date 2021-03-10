package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.StreamMetricMonitor
import io.scalac.extension.upstream.OpenTelemetryStreamMetricMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry.LongMetricObserverBuilderAdapter

object OpenTelemetryStreamMetricMonitor {
  case class MetricNames(runningStreams: String, streamActors: String)

  object MetricNames {
    private val defaults: MetricNames =
      MetricNames("akka_streams_running_streams", "akka_streams_actors")

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._

      config.tryValue("io.scalac.akka-monitoring.metrics.streams-metrics")(_.getConfig).map { streamMetricsConfig =>
        val runningStreams = streamMetricsConfig
          .tryValue("running-streams")(_.getString)
          .getOrElse(defaults.runningStreams)

        val streamActors = streamMetricsConfig
          .tryValue("stream-actors")(_.getString)
          .getOrElse(defaults.streamActors)

        MetricNames(runningStreams, streamActors)
      }
    }.getOrElse(defaults)
  }

  def apply(instrumentationName: String, config: Config): OpenTelemetryStreamMetricMonitor =
    new OpenTelemetryStreamMetricMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryStreamMetricMonitor(instrumentationName: String, metricNames: MetricNames)
    extends StreamMetricMonitor {

  import StreamMetricMonitor._

  private val meter = OpenTelemetry
    .getGlobalMeter(instrumentationName)

  private val runningStreamsRecorder = new LongMetricObserverBuilderAdapter(
    meter
      .longValueObserverBuilder(metricNames.runningStreams)
      .setDescription("Amount of running streams on a system")
  )

  private val streamActorsRecorder = new LongMetricObserverBuilderAdapter(
    meter
      .longValueObserverBuilder(metricNames.streamActors)
      .setDescription("Amount of actors running streams on a system")
  )

  override def bind(labels: Labels): BoundMonitor = new StreamMetricsBoundMonitor(labels)

  class StreamMetricsBoundMonitor(labels: Labels) extends BoundMonitor {
    private val openTelemetryLabels = LabelsFactory.of(labelSerializer.serialize(labels))

    override val runningStreams = runningStreamsRecorder.createObserver(openTelemetryLabels)

    override val streamActors = streamActorsRecorder.createObserver(openTelemetryLabels)

    override def unbind(): Unit =
      runningStreamsRecorder.removeObserver(openTelemetryLabels)
    streamActorsRecorder.removeObserver(openTelemetryLabels)
  }
}
