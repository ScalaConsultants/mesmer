package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.{ MetricObserver, RegisterRoot, StreamMetricMonitor }
import io.scalac.extension.upstream.OpenTelemetryStreamMetricMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry.{ LongSumObserverBuilderAdapter, SynchronousInstrumentFactory }

object OpenTelemetryStreamMetricMonitor {
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

  def apply(instrumentationName: String, config: Config): OpenTelemetryStreamMetricMonitor =
    new OpenTelemetryStreamMetricMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryStreamMetricMonitor(instrumentationName: String, metricNames: MetricNames)
    extends StreamMetricMonitor {

  import StreamMetricMonitor._

  private val meter = OpenTelemetry
    .getGlobalMeter(instrumentationName)

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

    override val runningStreamsTotal =
      metricRecorder(runningStreamsTotalRecorder, openTelemetryLabels).register(this)

    override val streamActorsTotal =
      metricRecorder(streamActorsTotalRecorder, openTelemetryLabels).register(this)

    override lazy val streamProcessedMessages: MetricObserver[Long, Labels] =
      streamProcessedMessagesBuilder.createObserver(this)
  }
}
