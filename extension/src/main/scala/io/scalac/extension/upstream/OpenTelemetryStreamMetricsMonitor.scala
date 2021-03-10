package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.{ LazyMetricObserver, StreamMetricMonitor }
import io.scalac.extension.upstream.OpenTelemetryStreamMetricMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry.{
  LazyLongSumObserverBuilderAdapter,
  ObserverHandle,
  OpenTelemetryLabelsConverter,
  WrappedLongValueRecorder
}

object OpenTelemetryStreamMetricMonitor {
  case class MetricNames(runningStreams: String, streamActors: String, streamProcessed: String)

  object MetricNames {
    private val defaults: MetricNames =
      MetricNames("akka_streams_running_streams", "akka_streams_actors", "akka_stream_stream_processed")

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._

      config.tryValue("io.scalac.akka-monitoring.metrics.streams-metrics")(_.getConfig).map { streamMetricsConfig =>
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

  private implicit val otConverte: OpenTelemetryLabelsConverter[Labels] = _.toOpenTelemetry

  private val runningStreamsTotalRecorder = meter
    .longValueRecorderBuilder(metricNames.runningStreams)
    .setDescription("Amount of running streams on a system")
    .build()

  private val streamActorsTotalRecorder = meter
    .longValueRecorderBuilder(metricNames.streamActors)
    .setDescription("Amount of actors running streams on a system")
    .build()

  private val streamProcessedMessagesBuilder = new LazyLongSumObserverBuilderAdapter[Labels](
    meter
      .longSumObserverBuilder(metricNames.streamProcessed)
      .setDescription("Amount of messages processed by whole stream")
  )

  override def bind(labels: EagerLabels): BoundMonitor = new StreamMetricsBoundMonitor(labels)

  class StreamMetricsBoundMonitor(labels: EagerLabels) extends BoundMonitor {
    private val openTelemetryLabels           = labels.toOpenTelemetry
    private var unbinds: List[ObserverHandle] = Nil

    override val runningStreamsTotal =
      WrappedLongValueRecorder(runningStreamsTotalRecorder, openTelemetryLabels)

    override val streamActorsTotal =
      WrappedLongValueRecorder(streamActorsTotalRecorder, openTelemetryLabels)

    override lazy val streamProcessedMessages: LazyMetricObserver[Long, Labels] = {
      val (unbind, observer) = streamProcessedMessagesBuilder.createObserver()
      unbinds ::= unbind
      observer
    }

    override def unbind(): Unit = {
      unbinds.foreach(_.unbindObserver())
      runningStreamsTotal.unbind()
      streamActorsTotal.unbind()
    }
  }
}
