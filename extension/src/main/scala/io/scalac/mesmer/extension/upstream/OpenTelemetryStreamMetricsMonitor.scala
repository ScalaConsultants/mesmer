package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaStreamModule
import io.scalac.mesmer.extension.metric.Histogram
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.metric.StreamMetricsMonitor
import io.scalac.mesmer.extension.upstream.OpenTelemetryStreamMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry.LongSumObserverBuilderAdapter
import io.scalac.mesmer.extension.upstream.opentelemetry.SynchronousInstrumentFactory

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
    .histogramBuilder(metricNames.runningStreams)
    .ofLongs()
    .setDescription("Amount of running streams on a system")
    .build()

  private lazy val streamActorsTotalRecorder = meter
    .histogramBuilder(metricNames.streamActors)
    .ofLongs()
    .setDescription("Amount of actors running streams on a system")
    .build()

  private lazy val streamProcessedMessagesBuilder = new LongSumObserverBuilderAdapter[Attributes](
    meter
      .counterBuilder(metricNames.streamProcessed)
      .setDescription("Amount of messages processed by whole stream")
  )

  def bind(attributes: EagerAttributes): BoundMonitor = new StreamMetricsBoundMonitor(attributes)

  final class StreamMetricsBoundMonitor(attributes: EagerAttributes)
      extends BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {
    private val openTelemetryAttributes = AttributesFactory.of(attributes.serialize)

    lazy val runningStreamsTotal: Histogram[Long] =
      if (moduleConfig.runningStreamsTotal)
        histogram(runningStreamsTotalRecorder, openTelemetryAttributes)
      else noopHistogram

    lazy val streamActorsTotal: Histogram[Long] =
      if (moduleConfig.streamActorsTotal)
        histogram(streamActorsTotalRecorder, openTelemetryAttributes)
      else noopHistogram

    lazy val streamProcessedMessages: MetricObserver[Long, Attributes] =
      if (moduleConfig.streamProcessedMessages) {
        streamProcessedMessagesBuilder.createObserver(this)
      } else MetricObserver.noop
  }
}
