package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }

import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.Module.CommonJars
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

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
  ) extends ActorSystemMetricsDef[Boolean] {
    lazy val enabled: Boolean = createdActors || terminatedActors
  }

  val defaultConfig: Config = ActorSystemModuleConfig(true, true)

  protected def extractFromConfig(config: TypesafeConfig): Config = {
    val createdActors = config.tryValue("created-actors")(_.getBoolean).getOrElse(defaultConfig.createdActors)

    val terminatedActors = config.tryValue("terminated-actors")(_.getBoolean).getOrElse(defaultConfig.createdActors)

    ActorSystemModuleConfig(createdActors, terminatedActors)
  }

  override type Jars[T] = ActorSystemJars[T]

  final case class ActorSystemJars[T](akkaActor: T, akkaActorTyped: T) extends CommonJars[T]

  def jarsFromLibraryInfo(info: LibraryInfo): Option[Jars[Version]] =
    for {
      actor      <- info.get(JarNames.akkaActor)
      actorTyped <- info.get(JarNames.akkaActorTyped)
    } yield ActorSystemJars(actor, actorTyped)

}
