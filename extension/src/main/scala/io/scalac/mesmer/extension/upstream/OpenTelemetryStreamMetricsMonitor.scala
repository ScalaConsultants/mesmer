package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaStreamModule
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.metric.StreamMetricsMonitor
import io.scalac.mesmer.extension.upstream.OpenTelemetryStreamMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry.LongSumObserverBuilderAdapter
import io.scalac.mesmer.extension.upstream.opentelemetry.SynchronousInstrumentFactory
import io.scalac.mesmer.extension.metric.MetricRecorder

object OpenTelemetryStreamMetricsMonitor {
  final case class MetricNames(runningStreams: String, streamActors: String, streamProcessed: String)

  object MetricNames extends MesmerConfiguration[MetricNames] {

    protected val mesmerConfig: String = "metrics.stream-metrics"

    val defaultConfig: MetricNames =
      MetricNames("akka_streams_running_streams", "akka_streams_actors", "akka_stream_processed_messages")

    protected def extractFromConfig(config: Config): MetricNames = {
      val runningStreams = config
        .tryValue("running-streams")(_.getString)
        .getOrElse(defaultConfig.runningStreams)

      val streamActors = config
        .tryValue("stream-actors")(_.getString)
        .getOrElse(defaultConfig.streamActors)

      val streamProcessed = config
        .tryValue("stream-processed")(_.getString)
        .getOrElse(defaultConfig.streamProcessed)

      MetricNames(runningStreams, streamActors, streamProcessed)
    }
  }

  def apply(
    meter: Meter,
    moduleConfig: AkkaStreamModule.StreamMetricsDef[Boolean],
    config: Config
  ): OpenTelemetryStreamMetricsMonitor =
    new OpenTelemetryStreamMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))
}

final class OpenTelemetryStreamMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaStreamModule.StreamMetricsDef[Boolean],
  metricNames: MetricNames
) extends StreamMetricsMonitor {

  import StreamMetricsMonitor._

  private lazy val runningStreamsTotalRecorder = meter
    .longValueRecorderBuilder(metricNames.runningStreams)
    .setDescription("Amount of running streams on a system")
    .build()

  private lazy val streamActorsTotalRecorder = meter
    .longValueRecorderBuilder(metricNames.streamActors)
    .setDescription("Amount of actors running streams on a system")
    .build()

  private lazy val streamProcessedMessagesBuilder = new LongSumObserverBuilderAdapter[Labels](
    meter
      .longSumObserverBuilder(metricNames.streamProcessed)
      .setDescription("Amount of messages processed by whole stream")
  )

  def bind(labels: EagerLabels): BoundMonitor = new StreamMetricsBoundMonitor(labels)

  final class StreamMetricsBoundMonitor(labels: EagerLabels)
      extends BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {
    private val openTelemetryLabels = LabelsFactory.of(labels.serialize)

    lazy val runningStreamsTotal: MetricRecorder[Long] =
      if (moduleConfig.runningStreamsTotal)
        metricRecorder(runningStreamsTotalRecorder, openTelemetryLabels).register(this)
      else noopMetricRecorder

    lazy val streamActorsTotal: MetricRecorder[Long] =
      if (moduleConfig.streamActorsTotal)
        metricRecorder(streamActorsTotalRecorder, openTelemetryLabels).register(this)
      else noopMetricRecorder

    lazy val streamProcessedMessages: MetricObserver[Long,Labels] =
      if (moduleConfig.streamProcessedMessages) {
        streamProcessedMessagesBuilder.createObserver(this)
      } else MetricObserver.noop
  }
}
