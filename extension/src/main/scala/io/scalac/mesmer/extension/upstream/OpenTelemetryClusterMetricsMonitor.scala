package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.Configuration
import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaClusterModule
import io.scalac.mesmer.extension.metric.ClusterMetricsMonitor
import io.scalac.mesmer.extension.metric._
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

  object MetricNames extends MesmerConfiguration[MetricNames] with Configuration {
    val defaultConfig: MetricNames =
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

  def apply(
    meter: Meter,
    moduleConfig: AkkaClusterModule.All[Boolean],
    config: Config
  ): OpenTelemetryClusterMetricsMonitor =
    new OpenTelemetryClusterMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))
}

final class OpenTelemetryClusterMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaClusterModule.All[Boolean],
  metricNames: MetricNames
) extends ClusterMetricsMonitor {

  private lazy val shardsPerRegionRecorder = new GaugeBuilderAdapter[ClusterMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.shardPerEntity)
      .ofLongs()
      .setDescription("Amount of shards in region")
  )

  private lazy val entityPerRegionRecorder = new GaugeBuilderAdapter[ClusterMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.entityPerRegion)
      .ofLongs()
      .setDescription("Amount of entities in region")
  )

  private lazy val reachableNodeCounter = meter
    .upDownCounterBuilder(metricNames.reachableNodes)
    .setDescription("Amount of reachable nodes")
    .build()

  private lazy val unreachableNodeCounter = meter
    .upDownCounterBuilder(metricNames.unreachableNodes)
    .setDescription("Amount of unreachable nodes")
    .build()

  private lazy val shardRegionsOnNodeRecorder = new GaugeBuilderAdapter[ClusterMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.shardRegionsOnNode)
      .ofLongs()
      .setDescription("Amount of shard regions on node")
  )

  private lazy val entitiesOnNodeObserver = new GaugeBuilderAdapter[ClusterMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.entitiesOnNode)
      .ofLongs()
      .setDescription("Amount of entities on node")
  )

  private lazy val nodeDownCounter = meter
    .counterBuilder(metricNames.nodeDown)
    .setDescription("Counter for node down events")
    .build()

  def bind(attributes: ClusterMetricsMonitor.Attributes): ClusterBoundMonitor = new ClusterBoundMonitor(attributes)

  final class ClusterBoundMonitor(attributes: ClusterMetricsMonitor.Attributes)
      extends opentelemetry.Synchronized(meter)
      with ClusterMetricsMonitor.BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {

    protected val otAttributes: Attributes = AttributesFactory.of(attributes.serialize)

    lazy val shardPerRegions: MetricObserver[Long, ClusterMetricsMonitor.Attributes] =
      if (moduleConfig.shardPerRegions) shardsPerRegionRecorder.createObserver(this) else MetricObserver.noop

    lazy val entityPerRegion: MetricObserver[Long, ClusterMetricsMonitor.Attributes] =
      if (moduleConfig.entityPerRegion) entityPerRegionRecorder.createObserver(this) else MetricObserver.noop

    lazy val shardRegionsOnNode: MetricObserver[Long, ClusterMetricsMonitor.Attributes] =
      if (moduleConfig.shardRegionsOnNode) shardRegionsOnNodeRecorder.createObserver(this) else MetricObserver.noop

    lazy val entitiesOnNode: MetricObserver[Long, ClusterMetricsMonitor.Attributes] =
      if (moduleConfig.entitiesOnNode) entitiesOnNodeObserver.createObserver(this) else MetricObserver.noop

    lazy val reachableNodes: UpDownCounter[Long] with Instrument[Long] =
      if (moduleConfig.reachableNodes) upDownCounter(reachableNodeCounter, otAttributes) else noopUpDownCounter

    lazy val unreachableNodes: UpDownCounter[Long] with Instrument[Long] =
      if (moduleConfig.unreachableNodes) upDownCounter(unreachableNodeCounter, otAttributes)
      else noopUpDownCounter

    lazy val nodeDown: Counter[Long] with Instrument[Long] =
      if (moduleConfig.nodeDown) counter(nodeDownCounter, otAttributes) else noopCounter

  }
}
