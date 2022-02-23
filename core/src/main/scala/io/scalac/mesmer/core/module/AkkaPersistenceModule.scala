package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }

import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.Module.CommonJars
import io.scalac.mesmer.core.typeclasses.Combine
import io.scalac.mesmer.core.typeclasses.Traverse
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

sealed trait AkkaPersistenceMetricsModule extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaPersistenceMetricsDef[T]

  trait AkkaPersistenceMetricsDef[T] {
    def recoveryTime: T
    def recoveryTotal: T
    def persistentEvent: T
    def persistentEventTotal: T
    def snapshot: T
  }
}

object AkkaPersistenceModule extends MesmerModule with AkkaPersistenceMetricsModule {
  override type Metrics[T] = AkkaPersistenceMetricsDef[T]

  val name: String = "akka-persistence"

  final case class Impl[T](
    recoveryTime: T,
    recoveryTotal: T,
    persistentEvent: T,
    persistentEventTotal: T,
    snapshot: T
  ) extends AkkaPersistenceMetricsDef[T]

  val defaultConfig: AkkaPersistenceModule.Result = Impl(true, true, true, true, true)

  protected def fromMap(properties: Map[String, Boolean]): AkkaPersistenceModule.Config = {
    val enabled = properties.getOrElse("enabled", true)

    if (enabled) {
      Impl(
        recoveryTime = properties.getOrElse("recovery.time", defaultConfig.recoveryTime),
        recoveryTotal = properties.getOrElse("recovery.total", defaultConfig.recoveryTotal),
        persistentEvent = properties.getOrElse("persistent.event", defaultConfig.persistentEvent),
        persistentEventTotal = properties.getOrElse("persistent.event.total", defaultConfig.persistentEventTotal),
        snapshot = properties.getOrElse("snapshot", defaultConfig.snapshot)
      )
    } else Impl(false, false, false, false, false)

  }


  override type All[T]  = Metrics[T]
  override type Jars[T] = AkkaPersistenceJars[T]

  final case class AkkaPersistenceJars[T](akkaActor: T, akkaActorTyped: T, akkaPersistence: T, akkaPersistenceTyped: T)
      extends CommonJars[T]

  def jarsFromLibraryInfo(info: LibraryInfo): Option[Jars[Version]] =
    for {
      actor            <- info.get(JarNames.akkaActor)
      actorTyped       <- info.get(JarNames.akkaActorTyped)
      persistence      <- info.get(JarNames.akkaPersistence)
      persistenceTyped <- info.get(JarNames.akkaPersistenceTyped)
    } yield AkkaPersistenceJars(actor, actorTyped, persistence, persistenceTyped)

  implicit val combineConfig: Combine[All[Boolean]] = (first, second) =>
    Impl(
      recoveryTime = first.recoveryTime && second.recoveryTime,
      recoveryTotal = first.recoveryTotal && second.recoveryTotal,
      persistentEvent = first.persistentEvent && second.persistentEvent,
      persistentEventTotal = first.persistentEventTotal && second.persistentEventTotal,
      snapshot = first.snapshot && second.snapshot
    )

  implicit val traverseAll: Traverse[All] = new Traverse[All] {
    def sequence[T](obj: All[T]): Seq[T] = Seq(
      obj.recoveryTime,
      obj.recoveryTotal,
      obj.persistentEvent,
      obj.persistentEventTotal,
      obj.snapshot
    )
  }

}
