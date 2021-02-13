package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.{ AsynchronousInstrument, Meter }

import io.scalac.extension.metric.{ ActorMetricMonitor, MetricObserver, MetricRecorder, Unbind }
import io.scalac.extension.upstream.OpenTelemetryActorMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryActorMetricsMonitor {

  case class MetricNames(mailboxSize: String)
  object MetricNames {
    def default: MetricNames = MetricNames("mailbox_size")
    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._
      val defaultCached = default
      config
        .tryValue("io.scalac.akka-monitoring.metrics.actor-metrics")(
          _.getConfig
        )
        .map { actorMetricsConfig =>
          val mailboxSize =
            actorMetricsConfig.tryValue("mailbox-size")(_.getString).getOrElse(defaultCached.mailboxSize)
          MetricNames(mailboxSize)
        }
        .getOrElse(defaultCached)
    }
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

  override def bind(labels: ActorMetricMonitor.Labels): OpenTelemetryBoundMonitor =
    new OpenTelemetryBoundMonitor(
      LabelsFactory.of(LabelNames.ActorPath -> labels.actorPath)(LabelNames.Node -> labels.node)
    )

  class OpenTelemetryBoundMonitor(labels: Labels) extends ActorMetricMonitor.BoundMonitor with Synchronized {

    override val mailboxSize: MetricObserver[Long] =
      WrappedLongValueObserver(mailboxSizeObserver, labels)

    override def unbind(): Unit =
      mailboxSizeObserver.remove(labels)

  }

}
