package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Labels

import io.scalac.extension.metric.{ ActorMetricMonitor, MetricObserver, MetricRecorder }
import io.scalac.extension.upstream.OpenTelemetryActorMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryActorMetricsMonitor {

  case class MetricNames(mailboxSize: String, mailboxTime: String, stashSize: String)
  object MetricNames {

    def default: MetricNames =
      MetricNames(
        "mailbox_size",
        "mailbox_time",
        "stash_size"
      )

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._
      val defaultCached = default

      config
        .tryValue("io.scalac.akka-monitoring.metrics.actor-metrics")(
          _.getConfig
        )
        .map { clusterMetricsConfig =>
          val mailboxSize = clusterMetricsConfig
            .tryValue("mailbox-size")(_.getString)
            .getOrElse(defaultCached.mailboxSize)

          val mailboxTime = clusterMetricsConfig
            .tryValue("mailbox-time")(_.getString)
            .getOrElse(defaultCached.mailboxTime)

          val stashSize = clusterMetricsConfig
            .tryValue("stash-size")(_.getString)
            .getOrElse(defaultCached.stashSize)

          MetricNames(mailboxSize, mailboxTime, stashSize)
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

  private val mailboxSizeObserver = new LongMetricObserverBuilderAdapter(
    meter
      .longValueObserverBuilder(metricNames.mailboxSize)
      .setDescription("Tracks the size of an Actor's mailbox")
  )

  private val mailboxTimeObserver = new LongMetricObserverBuilderAdapter(
    meter
      .longValueObserverBuilder(metricNames.mailboxTime)
      .setDescription("Tracks the time of an message in an Actor's mailbox")
  )

  private val stashSizeCounter = meter
    .longValueRecorderBuilder(metricNames.stashSize)
    .setDescription("Tracks the size of an Actor's stash")
    .build()

  override def bind(labels: ActorMetricMonitor.Labels): OpenTelemetryBoundMonitor =
    new OpenTelemetryBoundMonitor(
      LabelsFactory.of((LabelNames.ActorPath -> labels.actorPath) +: labels.tags.toSeq.flatMap(_.serialize): _*)(
        LabelNames.Node -> labels.node
      )
    )

  class OpenTelemetryBoundMonitor(labels: Labels) extends ActorMetricMonitor.BoundMonitor with Synchronized {

    val mailboxSize: MetricObserver[Long] =
      mailboxSizeObserver.createObserver(labels)

    val mailboxTime: MetricObserver[Long] =
      mailboxTimeObserver.createObserver(labels)

    val stashSize: MetricRecorder[Long] with WrappedSynchronousInstrument[Long] =
      WrappedLongValueRecorder(stashSizeCounter, labels)

    def unbind(): Unit = {
      mailboxSizeObserver.removeObserver(labels)
      stashSize.unbind()
    }

  }

}
