package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.metric._
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

  def apply(meter: Meter, config: Config): OpenTelemetryClusterMetricsMonitor =
    new OpenTelemetryClusterMetricsMonitor(meter, MetricNames.fromConfig(config))
}

class OpenTelemetryClusterMetricsMonitor(meter: Meter, metricNames: MetricNames) extends ClusterMetricsMonitor {

  private val shardsPerRegionRecorder = new LongMetricObserverBuilderAdapter[ClusterMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.shardPerEntity)
      .setDescription("Amount of shards in region")
  )

  private val entityPerRegionRecorder = new LongMetricObserverBuilderAdapter[ClusterMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.entityPerRegion)
      .setDescription("Amount of entities in region")
  )

  private val reachableNodeCounter = meter
    .longUpDownCounterBuilder(metricNames.reachableNodes)
    .setDescription("Amount of reachable nodes")
    .build()

  private val unreachableNodeCounter = meter
    .longUpDownCounterBuilder(metricNames.unreachableNodes)
    .setDescription("Amount of unreachable nodes")
    .build()

  private val shardRegionsOnNodeRecorder = new LongMetricObserverBuilderAdapter[ClusterMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.shardRegionsOnNode)
      .setDescription("Amount of shard regions on node")
  )

  private val entitiesOnNodeObserver = new LongMetricObserverBuilderAdapter[ClusterMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.entitiesOnNode)
      .setDescription("Amount of entities on node")
  )

  private val nodeDownCounter = meter
    .longCounterBuilder(metricNames.nodeDown)
    .setDescription("Counter for node down events")
    .build()

  def bind(labels: ClusterMetricsMonitor.Labels): ClusterBoundMonitor = new ClusterBoundMonitor(labels)
//    new ClusterBoundMonitor(LabelsFactory.of(LabelNames.Node -> labels.node)(LabelNames.Region -> labels.region))

  class ClusterBoundMonitor(labels: ClusterMetricsMonitor.Labels)
      extends opentelemetry.Synchronized(meter)
      with ClusterMetricsMonitor.BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {

    private val otLabels = LabelsFactory.of(labels.serialize)

    val shardPerRegions: MetricObserver[Long, ClusterMetricsMonitor.Labels] =
      shardsPerRegionRecorder.createObserver(this)

    val entityPerRegion: MetricObserver[Long, ClusterMetricsMonitor.Labels] =
      entityPerRegionRecorder.createObserver(this)

    val shardRegionsOnNode: MetricObserver[Long, ClusterMetricsMonitor.Labels] =
      shardRegionsOnNodeRecorder.createObserver(this)

    val entitiesOnNode: MetricObserver[Long, ClusterMetricsMonitor.Labels] =
      entitiesOnNodeObserver.createObserver(this)

    val reachableNodes: UpDownCounter[Long] with Instrument[Long] =
      upDownCounter(reachableNodeCounter, otLabels)(this)

    val unreachableNodes: UpDownCounter[Long] with Instrument[Long] =
      upDownCounter(unreachableNodeCounter, otLabels)(this)

    val nodeDown: Counter[Long] with Instrument[Long] =
      counter(nodeDownCounter, otLabels)(this)

  }
}
