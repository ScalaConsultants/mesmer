package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.extension.metric.MetricObserver
import io.scalac.extension.metric.RegisterRoot
import io.scalac.extension.metric.StreamMetricMonitor
import io.scalac.extension.upstream.OpenTelemetryStreamMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry.LongSumObserverBuilderAdapter
import io.scalac.extension.upstream.opentelemetry.SynchronousInstrumentFactory
import io.scalac.extension.upstream.opentelemetry.WrappedLongValueRecorder

object OpenTelemetryStreamMetricsMonitor {
  case class MetricNames(runningStreams: String, streamActors: String, streamProcessed: String)

  object MetricNames {
    private val defaults: MetricNames =
      MetricNames("akka_streams_running_streams", "akka_streams_actors", "akka_stream_processed_messages")

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._

      config.tryValue("io.scalac.akka-monitoring.metrics.stream-metrics")(_.getConfig).map { streamMetricsConfig =>
        val runningStreams = streamMetricsConfig
          .tryValue("running-streams")(_.getString)
          .getOrElse(defaults.runningStreams)

        val streamActors = streamMetricsConfig
          .tryValue("stream-actors")(_.getString)
          .getOrElse(defaults.streamActors)

        val streamProcessed = streamMetricsConfig
          .tryValue("stream-processed")(_.getString)
          .getOrElse(defaults.streamProcessed)

        MetricNames(runningStreams, streamActors, streamProcessed)
      }
    }.getOrElse(defaults)
  }

  def apply(meter: Meter, config: Config): OpenTelemetryStreamMetricsMonitor =
    new OpenTelemetryStreamMetricsMonitor(meter, MetricNames.fromConfig(config))
}

final class OpenTelemetryStreamMetricsMonitor(meter: Meter, metricNames: MetricNames) extends StreamMetricMonitor {

  import StreamMetricMonitor._

  private val runningStreamsTotalRecorder = meter
    .longValueRecorderBuilder(metricNames.runningStreams)
    .setDescription("Amount of running streams on a system")
    .build()

  private val streamActorsTotalRecorder = meter
    .longValueRecorderBuilder(metricNames.streamActors)
    .setDescription("Amount of actors running streams on a system")
    .build()

  private val streamProcessedMessagesBuilder = new LongSumObserverBuilderAdapter[Labels](
    meter
      .longSumObserverBuilder(metricNames.streamProcessed)
      .setDescription("Amount of messages processed by whole stream")
  )

  override def bind(labels: EagerLabels): BoundMonitor = new StreamMetricsBoundMonitor(labels)

  class StreamMetricsBoundMonitor(labels: EagerLabels)
      extends BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {
    private val openTelemetryLabels = LabelsFactory.of(labels.serialize)

    override val runningStreamsTotal: WrappedLongValueRecorder =
      metricRecorder(runningStreamsTotalRecorder, openTelemetryLabels).register(this)

    override val streamActorsTotal: WrappedLongValueRecorder =
      metricRecorder(streamActorsTotalRecorder, openTelemetryLabels).register(this)

    override lazy val streamProcessedMessages: MetricObserver[Long, Labels] =
      streamProcessedMessagesBuilder.createObserver(this)
  }
}
