package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.{ ActorSystemMonitor, MetricRecorder }
import io.scalac.extension.upstream.OpenTelemetryActorSystemMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry.WrappedLongValueRecorder

object OpenTelemetryActorSystemMonitor {
  case class MetricNames(actorsCount: String, scanDuration: String)

  object MetricNames {
    val default: MetricNames = MetricNames("actors_count", "actor_tree_scan_milliseconds")

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._

      config
        .tryValue("io.scalac.akka-monitoring.metrics.actor-system-metrics")(_.getConfig)
        .map { config =>
          val actorsCount  = config.tryValue("actors-count")(_.getString).getOrElse(default.actorsCount)
          val scanDuration = config.tryValue("scan-duration")(_.getString).getOrElse(default.scanDuration)

          MetricNames(actorsCount, scanDuration)
        }
        .getOrElse(default)
    }

  }
  def apply(instrumentationName: String, config: Config): OpenTelemetryActorSystemMonitor =
    new OpenTelemetryActorSystemMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryActorSystemMonitor(instrumentationName: String, metricNames: MetricNames)
    extends ActorSystemMonitor {

  override type Bound = BoundMonitor

  private val actorCountRecorder = OpenTelemetry
    .getGlobalMeter(instrumentationName)
    .longValueRecorderBuilder(metricNames.actorsCount)
    .setDescription("Amount of local actors in actor system")
    .build()

  private val scanDurationRecorder = OpenTelemetry
    .getGlobalMeter(instrumentationName)
    .longValueRecorderBuilder(metricNames.scanDuration)
    .setDescription("Amount of ms it took to scan all actors in the system")
    .build()

  override def bind(labels: ActorSystemMonitor.Labels): BoundMonitor = new BoundMonitor {
    val openTelemetryLabels                   = labels.toOpenTelemetry
    override def actors: MetricRecorder[Long] = WrappedLongValueRecorder(actorCountRecorder, openTelemetryLabels)

    override def actorTreeScanDuration: MetricRecorder[Long] =
      WrappedLongValueRecorder(scanDurationRecorder, openTelemetryLabels)
  }
}
