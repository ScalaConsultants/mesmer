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
    entitiesOnNode: String,
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
        "entities_on_node",
        "reachable_nodes",
        "unreachable_nodes",
        "node_down_total"
      )

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._
      val defaultCached = default

      config
        .tryValue("io.scalac.akka-monitoring.metrics.cluster-metrics")(
          _.getConfig
        )
        .map { clusterMetricsConfig =>
          val shardsPerRegion = clusterMetricsConfig
            .tryValue("shards-per-region")(_.getString)
            .getOrElse(defaultCached.shardPerEntity)

          val entitiesPerRegion = clusterMetricsConfig
            .tryValue("entities-per-region")(_.getString)
            .getOrElse(defaultCached.entityPerRegion)

          val shardRegionsOnNode = clusterMetricsConfig
            .tryValue("shard-regions-on-node")(_.getString)
            .getOrElse(defaultCached.shardRegionsOnNode)

          val entitiesOnNode = clusterMetricsConfig
            .tryValue("entities-on-node")(_.getString)
            .getOrElse(defaultCached.shardRegionsOnNode)

          val reachableNodes = clusterMetricsConfig
            .tryValue("reachable-nodes")(_.getString)
            .getOrElse(defaultCached.reachableNodes)

          val unreachableNodes = clusterMetricsConfig
            .tryValue("unreachable-nodes")(_.getString)
            .getOrElse(defaultCached.unreachableNodes)

          val nodesDown = clusterMetricsConfig
            .tryValue("node-down")(_.getString)
            .getOrElse(defaultCached.nodeDown)

          MetricNames(
            shardsPerRegion,
            entitiesPerRegion,
            shardRegionsOnNode,
            entitiesOnNode,
            reachableNodes,
            unreachableNodes,
            nodesDown
          )
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

  private val shardRegionsOnNodeRecorder = meter
    .longValueRecorderBuilder(metricNames.shardRegionsOnNode)
    .setDescription("Amount of shard regions on node")
    .build()

  private val entitiesOnNodeRecorder = meter
    .longValueRecorderBuilder(metricNames.entitiesOnNode)
    .setDescription("Amount of entities on node")
    .build()

  private val nodeDownCounter = meter
    .longCounterBuilder(metricNames.nodeDown)
    .setDescription("Counter for node down events")
    .build()

  override def bind(node: Node): ClusterBoundMonitor = {
    val boundLabels = Labels.of("node", node)
    new ClusterBoundMonitor(boundLabels)
  }

  class ClusterBoundMonitor(labels: Labels) extends ClusterMetricsMonitor.BoundMonitor with opentelemetry.Synchronized {
    override val shardPerRegions: MetricRecorder[Long] with Instrument[Long] =
      WrappedLongValueRecorder(shardsPerRegionRecorder, labels)

    override val entityPerRegion: MetricRecorder[Long] with Instrument[Long] =
      WrappedLongValueRecorder(entityPerRegionRecorder, labels)

    override val shardRegionsOnNode: MetricRecorder[Long] with Instrument[Long] =
      WrappedLongValueRecorder(shardRegionsOnNodeRecorder, labels)

    override val entitiesOnNode: MetricRecorder[Long] with Instrument[Long] =
      WrappedLongValueRecorder(entitiesOnNodeRecorder, labels)

    override val reachableNodes: Counter[Long] with Instrument[Long] =
      WrappedUpDownCounter(reachableNodeCounter, labels)

    override val unreachableNodes: Counter[Long] with Instrument[Long] =
      WrappedUpDownCounter(unreachableNodeCounter, labels)

    override val nodeDown: UpCounter[Long] with Instrument[Long] = WrappedCounter(nodeDownCounter, labels)

    override def unbind(): Unit = {
      shardPerRegions.unbind()
      entityPerRegion.unbind()
      shardRegionsOnNode.unbind()
      entitiesOnNode.unbind()
      reachableNodes.unbind()
      unreachableNodes.unbind()
      nodeDown.unbind()
    }
  }
}
