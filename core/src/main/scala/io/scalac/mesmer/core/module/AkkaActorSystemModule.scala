package io.scalac.mesmer.core.module

sealed trait AkkaActorSystemMetricsModule extends MetricsModule {
  this: Module =>
  override type Metrics[T] <: ActorSystemMetricsDef[T]

  trait ActorSystemMetricsDef[T] {
    def createdActors: T
    def terminatedActors: T
  }
}

object AkkaActorSystemModule extends MesmerModule with AkkaActorSystemMetricsModule {
  lazy val name: String = "akkasystem"

  override type Metrics[T] = ActorSystemMetricsDef[T]
  override type All[T]     = Metrics[T]

  final case class ActorSystemModuleConfig(
    createdActors: Boolean,
    terminatedActors: Boolean
  ) extends ActorSystemMetricsDef[Boolean] {
    lazy val enabled: Boolean = createdActors || terminatedActors
  }

  val defaultConfig: Config = ActorSystemModuleConfig(true, true)

  protected def fromMap(properties: Map[String, Boolean]): AkkaActorSystemModule.Config =
    ActorSystemModuleConfig(
      createdActors = properties.getOrElse("created.actors", defaultConfig.createdActors),
      terminatedActors = properties.getOrElse("terminated.actors", defaultConfig.terminatedActors)
    )

}
