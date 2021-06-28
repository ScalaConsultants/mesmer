package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }

import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

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

  protected def extractFromConfig(config: TypesafeConfig): AkkaClusterModule.Result = {
    val moduleEnabled = config
      .tryValue("enabled")(_.getBoolean)
      .getOrElse(true)

    if (moduleEnabled) {
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

      Impl[Boolean](
        shardPerRegions = shardsPerRegion,
        entityPerRegion = entitiesPerRegion,
        shardRegionsOnNode = shardRegionsOnNode,
        entitiesOnNode = entitiesOnNode,
        reachableNodes = reachableNodes,
        unreachableNodes = unreachableNodes,
        nodeDown = nodesDown
      )
    } else Impl[Boolean](false, false, false, false, false, false, false)

  }

  override type All[T]     = Metrics[T]
  override type AkkaJar[T] = Jars[T]

  final case class Jars[T](akkaCluster: T, akkaClusterTyped: T)

  override def jarsFromLibraryInfo(info: LibraryInfo): Option[AkkaJar[Version]] =
    for {
      cluster      <- info.get(requiredAkkaJars.akkaCluster)
      clusterTyped <- info.get(requiredAkkaJars.akkaClusterTyped)
    } yield Jars(cluster, clusterTyped)

  val requiredAkkaJars: AkkaJar[String] = Jars("akka-cluster", "akka-cluster-typed")
}
