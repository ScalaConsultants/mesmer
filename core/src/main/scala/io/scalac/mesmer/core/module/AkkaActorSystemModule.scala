package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }

sealed trait AkkaActorSystemMetricsModule extends MetricsModule {
  this: Module =>
  override type Metrics[T] <: ActorSystemMetricsDef[T]

  trait ActorSystemMetricsDef[T] {
    def createdActors: T
    def terminatedActors: T
  }
}

object AkkaActorSystemModule extends MesmerModule with AkkaActorSystemMetricsModule {
  val name: String = "akka-system"

  override type Metrics[T] = ActorSystemMetricsDef[T]
  override type All[T]     = Metrics[T]

  final case class ActorSystemModuleConfig(
    createdActors: Boolean,
    terminatedActors: Boolean
  ) extends ActorSystemMetricsDef[Boolean]
      with ModuleConfig {
    lazy val enabled: Boolean = createdActors || terminatedActors
  }

  protected val defaultConfig: Config = ActorSystemModuleConfig(true, true)

  protected def extractFromConfig(config: TypesafeConfig): Config = {
    val createdActors = config.tryValue("created-actors")(_.getBoolean).getOrElse(defaultConfig.createdActors)

    val terminatedActors = config.tryValue("terminated-actors")(_.getBoolean).getOrElse(defaultConfig.createdActors)

    ActorSystemModuleConfig(createdActors, terminatedActors)
  }
}
