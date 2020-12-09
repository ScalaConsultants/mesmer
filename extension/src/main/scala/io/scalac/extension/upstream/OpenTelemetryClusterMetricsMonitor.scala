package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Labels
import io.scalac.extension.metric._
import io.scalac.extension.model._
import io.scalac.extension.upstream.OpenTelemetryClusterMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryClusterMetricsMonitor {
  case class MetricNames(
    shardPerEntity: String,
    entityPerRegion: String,
    shardRegionsOnNode: String,
    reachableNodes: String,
    unreachableNodes: String,
    nodeDown: String
  )

  object MetricNames {
    def default: MetricNames =
      MetricNames(
        "shards_per_region",
        "entities_per_region",
        "shard_regions_on_node",
        "reachable_nodes",
        "unreachable_nodes",
        "node_down_total"
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

          val down = clusterMetricsConfig
            .tryValue("node-down")(_.getString)
            .getOrElse(defaultCached.nodeDown)

          MetricNames(shards, entities, regions, reachable, unreachable, down)
        }
        .getOrElse(defaultCached)
    }
  }

  def apply(instrumentationName: String, config: Config): OpenTelemetryClusterMetricsMonitor =
    new OpenTelemetryClusterMetricsMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryClusterMetricsMonitor(instrumentationName: String, val metricNames: MetricNames)
    extends ClusterMetricsMonitor {

  private[this] val meter = OpenTelemetry.getGlobalMeter(instrumentationName)

  private val shardsPerRegionRecorder = meter
    .longValueRecorderBuilder(metricNames.shardPerEntity)
    .setDescription("Amount of shards in region")
    .build()

  private val entityPerRegionRecorder = meter
    .longValueRecorderBuilder(metricNames.entityPerRegion)
    .setDescription("Amount of entities in region")
    .build()

  private val reachableNodeCounter = meter
    .longUpDownCounterBuilder(metricNames.reachableNodes)
    .setDescription("Amount of reachable nodes")
    .build()

  private val unreachableNodeCounter = meter
    .longUpDownCounterBuilder(metricNames.unreachableNodes)
    .setDescription("Amount of unreachable nodes")
    .build()

  private val regionsOnNode = meter
    .longValueRecorderBuilder(metricNames.shardRegionsOnNode)
    .setDescription("Amount of shard regions on node")
    .build()

  private val nodeDownCounter = meter
    .longCounterBuilder(metricNames.nodeDown)
    .setDescription("Counter for node down events")
    .build()

  override type Bound = ClusterBoundMonitor

  override def bind(node: Node): ClusterBoundMonitor = {
    val boundLabels = Labels.of("node", node)
    new ClusterBoundMonitor(boundLabels)
  }

  class ClusterBoundMonitor(labels: Labels) extends BoundMonitor with opentelemetry.Synchronized {
    override def shardPerRegions: MetricRecorder[Long] with Instrument[Long] =
      WrappedLongValueRecorder(shardsPerRegionRecorder, labels)

    override def entityPerRegion: MetricRecorder[Long] with Instrument[Long] =
      WrappedLongValueRecorder(entityPerRegionRecorder, labels)

    override def shardRegionsOnNode: MetricRecorder[Long] with Instrument[Long] =
      WrappedLongValueRecorder(regionsOnNode, labels)

    override def reachableNodes: Counter[Long] with Instrument[Long] =
      WrappedUpDownCounter(reachableNodeCounter, labels)

    override def unreachableNodes: Counter[Long] with Instrument[Long] =
      WrappedUpDownCounter(unreachableNodeCounter, labels)

    override def nodeDown: UpCounter[Long] with Instrument[Long] = WrappedCounter(nodeDownCounter, labels)
  }
}
