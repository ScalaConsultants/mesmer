package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.common.Labels
import io.opentelemetry.metrics.LongUpDownCounter.BoundLongUpDownCounter
import io.opentelemetry.metrics.LongValueRecorder.BoundLongValueRecorder
import io.opentelemetry.metrics.SynchronousInstrument.BoundInstrument
import io.scalac.extension.model._
import io.scalac.extension.upstream.OpenTelemetryClusterMetricsMonitor.MetricNames

sealed trait Metric[T]

object Metric {
  implicit class OpenTelemetryOps[T <: BoundInstrument](instrument: T) {
    def toMetricRecorder(
      implicit ev: T =:= BoundLongValueRecorder
    ): MetricRecorder[Long] = (value: Long) => ev(instrument).record(value)

    def toCounter(implicit ev: T =:= BoundLongUpDownCounter): Counter[Long] =
      new Counter[Long] {
        private val openTelemetryCounter = ev(instrument)
        override def incValue(value: Long): Unit =
          openTelemetryCounter.add(value)

        override def decValue(value: Long): Unit =
          openTelemetryCounter.add(-value)
      }
  }
}

trait MetricRecorder[T] extends Metric[T] {
  def setValue(value: T): Unit
}

trait Counter[T] extends Metric[T] {
  def incValue(value: T): Unit
  def decValue(value: T): Unit
}

trait ClusterMetricsMonitor {

  def bind(node: Node): BoundClusterMetricsMonitor

  trait BoundClusterMetricsMonitor {
    def shardPerRegions: MetricRecorder[Long]
    def entityPerRegion: MetricRecorder[Long]
    def reachableNodes: Counter[Long]
    def unreachableNodes: Counter[Long]
  }
}

object OpenTelemetryClusterMetricsMonitor {
  case class MetricNames(shardPerEntity: String,
                         entityPerRegion: String,
                         reachableNodes: String,
                         unreachableNodes: String)

  object MetricNames {
    def default: MetricNames =
      MetricNames(
        "shards_per_region",
        "entities_per_region",
        "reachable_nodes",
        "unreachable_nodes"
      )

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._
      val defaultCached = default

      config
        .tryValue("io.scalac.akka-cluster-monitoring.cluster-metrics")(
          _.getConfig
        )
        .map { clusterMetricsConfig =>
          val shards = clusterMetricsConfig
            .tryValue("shards-per-region")(_.getString)
            .getOrElse(defaultCached.shardPerEntity)

          val entities = clusterMetricsConfig
            .tryValue("entities-per-region")(_.getString)
            .getOrElse(defaultCached.entityPerRegion)

          val reachable = clusterMetricsConfig
            .tryValue("reachable-nodes")(_.getString)
            .getOrElse(defaultCached.reachableNodes)

          val unreachable = clusterMetricsConfig
            .tryValue("unreachable-nodes")(_.getString)
            .getOrElse(defaultCached.unreachableNodes)

          MetricNames(shards, entities, reachable, unreachable)
        }.getOrElse(defaultCached)
    }
  }
}

class OpenTelemetryClusterMetricsMonitor(instrumentationName: String,
                                         val metricNames: MetricNames)
    extends ClusterMetricsMonitor {
  override def bind(node: Node): BoundClusterMetricsMonitor = {
    val meter = OpenTelemetry.getMeter(instrumentationName)

    val boundShardsPerRegionRecorder = meter
      .longValueRecorderBuilder(metricNames.shardPerEntity)
      .setDescription("Amount of shards in region")
      .build()
      .bind(Labels.of("node", node))

    val boundEntityPerRegionRecorder = meter
      .longValueRecorderBuilder(metricNames.entityPerRegion)
      .setDescription("Amount of entities in region")
      .build()
      .bind(Labels.of("node", node))

    val boundReachableNodeCounter = meter
      .longUpDownCounterBuilder(metricNames.reachableNodes)
      .setDescription("Amount of entities in region")
      .build()
      .bind(Labels.of("node", node))

    val boundUnreachableNodeCounter = meter
      .longUpDownCounterBuilder(metricNames.unreachableNodes)
      .setDescription("Amount of entities in region")
      .build()
      .bind(Labels.of("node", node))

    import Metric._

    new BoundClusterMetricsMonitor {
      override def shardPerRegions: MetricRecorder[Long] =
        boundShardsPerRegionRecorder.toMetricRecorder
      override def entityPerRegion: MetricRecorder[Long] =
        boundEntityPerRegionRecorder.toMetricRecorder

      override def reachableNodes: Counter[Long] =
        boundReachableNodeCounter.toCounter

      override def unreachableNodes: Counter[Long] =
        boundUnreachableNodeCounter.toCounter
    }

  }
}
