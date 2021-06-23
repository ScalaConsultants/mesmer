package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }

sealed trait AkkaClusterMetricsModule extends MetricsModule {
  this: Module =>
  override type Metrics[T] <: AkkaClusterMetricsDef[T]

  trait AkkaClusterMetricsDef[T] {
    def shardPerRegions: T
    def entityPerRegion: T
    def shardRegionsOnNode: T
    def entitiesOnNode: T
    def reachableNodes: T
    def unreachableNodes: T
    def nodeDown: T
  }
}

object AkkaClusterModule extends MesmerModule with AkkaClusterMetricsModule {

  val name: String = "akka-cluster"

  final case class AkkaClusterModuleMetrics(
    shardPerRegions: Boolean,
    entityPerRegion: Boolean,
    shardRegionsOnNode: Boolean,
    entitiesOnNode: Boolean,
    reachableNodes: Boolean,
    unreachableNodes: Boolean,
    nodeDown: Boolean
  ) extends AkkaClusterMetricsDef[Boolean]
      with ModuleConfig {
    lazy val enabled: Boolean = shardPerRegions ||
      entityPerRegion ||
      shardRegionsOnNode ||
      entitiesOnNode ||
      reachableNodes ||
      unreachableNodes ||
      nodeDown
  }

  override type Metrics[T] = AkkaClusterMetricsDef[T]

   val defaultConfig: AkkaClusterModule.Result =
    AkkaClusterModuleMetrics(true, true, true, true, true, true, true)

  protected def extractFromConfig(config: TypesafeConfig): AkkaClusterModule.Result = {
    val shardsPerRegion = config
      .tryValue("shards-per-region")(_.getBoolean)
      .getOrElse(defaultConfig.shardPerRegions)

    val entitiesPerRegion = config
      .tryValue("entities-per-region")(_.getBoolean)
      .getOrElse(defaultConfig.entityPerRegion)

    val shardRegionsOnNode = config
      .tryValue("shard-regions-on-node")(_.getBoolean)
      .getOrElse(defaultConfig.shardRegionsOnNode)

    val entitiesOnNode = config
      .tryValue("entities-on-node")(_.getBoolean)
      .getOrElse(defaultConfig.entitiesOnNode)

    val reachableNodes = config
      .tryValue("reachable-nodes")(_.getBoolean)
      .getOrElse(defaultConfig.reachableNodes)

    val unreachableNodes = config
      .tryValue("unreachable-nodes")(_.getBoolean)
      .getOrElse(defaultConfig.unreachableNodes)

    val nodesDown = config
      .tryValue("node-down")(_.getBoolean)
      .getOrElse(defaultConfig.nodeDown)

    AkkaClusterModuleMetrics(
      shardPerRegions = shardsPerRegion,
      entityPerRegion = entitiesPerRegion,
      shardRegionsOnNode = shardRegionsOnNode,
      entitiesOnNode = entitiesOnNode,
      reachableNodes = reachableNodes,
      unreachableNodes = unreachableNodes,
      nodeDown = nodesDown
    )
  }

  override type All[T] = Metrics[T]
}
