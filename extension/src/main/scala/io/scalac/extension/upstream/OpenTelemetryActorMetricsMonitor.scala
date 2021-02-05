package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Labels

import OpenTelemetryActorMetricsMonitor.MetricNames
import io.scalac.extension.metric.{ ActorMonitor, Counter, MetricRecorder }
import io.scalac.extension.model.Path
import io.scalac.extension.upstream.opentelemetry.{
  Synchronized,
  WrappedLongValueRecorder,
  WrappedSynchronousInstrument,
  WrappedUpDownCounter
}

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

class OpenTelemetryActorMetricsMonitor(instrumentationName: String, metricNames: MetricNames) extends ActorMonitor {

  override type Bound = OpenTelemetryBoundMonitor

  private[this] val meter = OpenTelemetry.getGlobalMeter(instrumentationName)

  private val mailboxSizeCounter = meter
    .longValueRecorderBuilder(metricNames.mailboxSize)
    .setDescription("Tracks the size of an Actor's mailbox")
    .build()

  override def bind(labels: ActorMonitor.Labels): OpenTelemetryBoundMonitor =
    new OpenTelemetryBoundMonitor(
      LabelsFactory.of("actorPath" -> labels.actorPath)("node" -> labels.node)
    )

  class OpenTelemetryBoundMonitor(labels: Labels) extends BoundMonitor with Synchronized {
    override def mailboxSize: MetricRecorder[Long] with WrappedSynchronousInstrument[Long] =
      WrappedLongValueRecorder(mailboxSizeCounter, labels)
  }

}
