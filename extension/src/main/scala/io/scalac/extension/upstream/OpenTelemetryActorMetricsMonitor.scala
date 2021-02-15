package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.{ AsynchronousInstrument, Meter }

import io.scalac.extension.metric.{ ActorMetricMonitor, MetricObserver, MetricRecorder, Unbind }
import io.scalac.extension.upstream.OpenTelemetryActorMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryActorMetricsMonitor {

  case class MetricNames(mailboxSize: String, stashSize: String)
  object MetricNames
      extends MetricNamesCompanion[MetricNames](
        "io.scalac.akka-monitoring.metrics.actor-metrics",
        Seq("mailbox-size", "stash-size")
      ) {
    override protected def argsApply(args: Seq[String]): MetricNames = apply(args.head, args(1))
  }

  def apply(instrumentationName: String, config: Config): OpenTelemetryActorMetricsMonitor =
    new OpenTelemetryActorMetricsMonitor(instrumentationName, MetricNames.fromConfig(config))

}

class OpenTelemetryActorMetricsMonitor(instrumentationName: String, metricNames: MetricNames)
    extends ActorMetricMonitor {

  private val meter = OpenTelemetry.getGlobalMeter(instrumentationName)

  private val mailboxSizeObserver = new LongValueObserverAdapter(
    meter
      .longValueObserverBuilder(metricNames.mailboxSize)
      .setDescription("Tracks the size of an Actor's mailbox")
  )

  private val stashSizeCounter = meter
    .longValueRecorderBuilder(metricNames.stashSize)
    .setDescription("Tracks the size of an Actor's stash")
    .build()

  override def bind(labels: ActorMetricMonitor.Labels): OpenTelemetryBoundMonitor =
    new OpenTelemetryBoundMonitor(
      LabelsFactory.of(LabelNames.ActorPath -> labels.actorPath)(LabelNames.Node -> labels.node)
    )

  class OpenTelemetryBoundMonitor(labels: Labels) extends ActorMetricMonitor.BoundMonitor with Synchronized {

    override val mailboxSize: MetricObserver[Long] =
      WrappedLongValueObserver(mailboxSizeObserver, labels)

    override val stashSize: MetricRecorder[Long] with WrappedSynchronousInstrument[Long] =
      WrappedLongValueRecorder(stashSizeCounter, labels)

    override def unbind(): Unit = {
      mailboxSizeObserver.remove(labels)
      stashSize.unbind()
    }

  }

}
