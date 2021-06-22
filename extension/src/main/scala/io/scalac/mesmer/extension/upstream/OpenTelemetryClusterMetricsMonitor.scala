package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter
import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.extension.metric.{ ClusterMetricsMonitor, _ }
import io.scalac.mesmer.extension.upstream.OpenTelemetryClusterMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryClusterMetricsMonitor {
  final case class MetricNames(
    shardPerEntity: String,
    entityPerRegion: String,
    shardRegionsOnNode: String,
    entitiesOnNode: String,
    reachableNodes: String,
    unreachableNodes: String,
    nodeDown: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] {
    protected val defaultConfig: MetricNames =
      MetricNames(
        "akka_cluster_shards_per_region",
        "akka_cluster_entities_per_region",
        "akka_cluster_shard_regions_on_node",
        "akka_cluster_entities_on_node",
        "akka_cluster_reachable_nodes",
        "akka_cluster_unreachable_nodes",
        "akka_cluster_node_down_total"
      )

    protected val mesmerConfig: String = "metrics.cluster-metrics"

    protected def extractFromConfig(config: Config): MetricNames = {
      val shardsPerRegion = config
        .tryValue("shards-per-region")(_.getString)
        .getOrElse(defaultConfig.shardPerEntity)

      val entitiesPerRegion = config
        .tryValue("entities-per-region")(_.getString)
        .getOrElse(defaultConfig.entityPerRegion)

      val shardRegionsOnNode = config
        .tryValue("shard-regions-on-node")(_.getString)
        .getOrElse(defaultConfig.shardRegionsOnNode)

      val entitiesOnNode = config
        .tryValue("entities-on-node")(_.getString)
        .getOrElse(defaultConfig.entitiesOnNode)

      val reachableNodes = config
        .tryValue("reachable-nodes")(_.getString)
        .getOrElse(defaultConfig.reachableNodes)

      val unreachableNodes = config
        .tryValue("unreachable-nodes")(_.getString)
        .getOrElse(defaultConfig.unreachableNodes)

      val nodesDown = config
        .tryValue("node-down")(_.getString)
        .getOrElse(defaultConfig.nodeDown)

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
  }

  def apply(meter: Meter, config: Config): OpenTelemetryClusterMetricsMonitor =
    new OpenTelemetryClusterMetricsMonitor(meter, MetricNames.fromConfig(config))
}

final class OpenTelemetryClusterMetricsMonitor(meter: Meter, metricNames: MetricNames) extends ClusterMetricsMonitor {

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

    protected val otLabels = LabelsFactory.of(labels.serialize)

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
