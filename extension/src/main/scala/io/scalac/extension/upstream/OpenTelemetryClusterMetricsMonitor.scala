package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.common.Labels
import io.scalac.extension.metric._
import io.scalac.extension.model._
import io.scalac.extension.upstream.OpenTelemetryClusterMetricsMonitor.MetricNames

object OpenTelemetryClusterMetricsMonitor {
  case class MetricNames(
    shardPerEntity: String,
    entityPerRegion: String,
    shardRegionsOnNode: String,
    reachableNodes: String,
    unreachableNodes: String
  )

  object MetricNames {
    def default: MetricNames =
      MetricNames(
        "shards_per_region",
        "entities_per_region",
        "shard_regions_on_node",
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

          val regions = clusterMetricsConfig
            .tryValue("shard-regions-on-node")(_.getString)
            .getOrElse(defaultCached.shardRegionsOnNode)

          val reachable = clusterMetricsConfig
            .tryValue("reachable-nodes")(_.getString)
            .getOrElse(defaultCached.reachableNodes)

          val unreachable = clusterMetricsConfig
            .tryValue("unreachable-nodes")(_.getString)
            .getOrElse(defaultCached.unreachableNodes)

          MetricNames(shards, entities, regions, reachable, unreachable)
        }
        .getOrElse(defaultCached)
    }
  }
}

class OpenTelemetryClusterMetricsMonitor(instrumentationName: String, val metricNames: MetricNames)
    extends ClusterMetricsMonitor {
  import ClusterMetricsMonitor._

  override def bind(node: Node): BoundMonitor = {
    val meter = OpenTelemetry.getMeter(instrumentationName)

    val boundLabels = Labels.of("node", node)
    val boundShardsPerRegionRecorder = meter
      .longValueRecorderBuilder(metricNames.shardPerEntity)
      .setDescription("Amount of shards in region")
      .build()
      .bind(boundLabels)

    val boundEntityPerRegionRecorder = meter
      .longValueRecorderBuilder(metricNames.entityPerRegion)
      .setDescription("Amount of entities in region")
      .build()
      .bind(boundLabels)

    val boundReachableNodeCounter = meter
      .longUpDownCounterBuilder(metricNames.reachableNodes)
      .setDescription("Amount of reachable nodes")
      .build()
      .bind(boundLabels)

    val boundUnreachableNodeCounter = meter
      .longUpDownCounterBuilder(metricNames.unreachableNodes)
      .setDescription("Amount of unreachable nodes")
      .build()
      .bind(boundLabels)

    val boundRegionsOnNode = meter
      .longValueRecorderBuilder(metricNames.shardRegionsOnNode)
      .setDescription("Amount of shard regions on node")
      .build()
      .bind(boundLabels)

    import Metric._

    new BoundMonitor {
      override val shardPerRegions: MetricRecorder[Long] =
        boundShardsPerRegionRecorder.toMetricRecorder

      override val entityPerRegion: MetricRecorder[Long] =
        boundEntityPerRegionRecorder.toMetricRecorder

      override val reachableNodes: Counter[Long] =
        boundReachableNodeCounter.toCounter

      override val unreachableNodes: Counter[Long] =
        boundUnreachableNodeCounter.toCounter

      override val shardRegionsOnNode: MetricRecorder[Long] =
        boundRegionsOnNode.toMetricRecorder
    }

  }
}
