package io.scalac.mesmer.core.module

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

  lazy val name: String = "akkacluster"

  final case class Impl[T](
    shardPerRegions: T,
    entityPerRegion: T,
    shardRegionsOnNode: T,
    entitiesOnNode: T,
    reachableNodes: T,
    unreachableNodes: T,
    nodeDown: T
  ) extends AkkaClusterMetricsDef[T]

  override type Metrics[T] = AkkaClusterMetricsDef[T]

  val defaultConfig: AkkaClusterModule.Result =
    Impl[Boolean](true, true, true, true, true, true, true)

  protected def fromMap(properties: Map[String, Boolean]): AkkaClusterModule.Config = {
    val enabled = properties.getOrElse("enabled", true)

    Impl(
      shardPerRegions = properties.getOrElse("shards.per.region", defaultConfig.shardPerRegions),
      entityPerRegion = properties.getOrElse("entities.per.region", defaultConfig.entityPerRegion),
      shardRegionsOnNode = properties.getOrElse("shard.regions.on.node", defaultConfig.shardRegionsOnNode),
      entitiesOnNode = properties.getOrElse("entities.on.node", defaultConfig.entitiesOnNode),
      reachableNodes = properties.getOrElse("reachable.nodes", defaultConfig.reachableNodes),
      unreachableNodes = properties.getOrElse("unreachable.nodes", defaultConfig.unreachableNodes),
      nodeDown = properties.getOrElse("node.down", defaultConfig.nodeDown)
    )

  }

  override type All[T] = Metrics[T]
}
