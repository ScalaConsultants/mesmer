package io.scalac.mesmer.core.module

import io.scalac.mesmer.core.typeclasses.Combine
import io.scalac.mesmer.core.typeclasses.Traverse

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

  lazy val name: String = "akkapersistence"

  final case class Impl[T](
    recoveryTime: T,
    recoveryTotal: T,
    persistentEvent: T,
    persistentEventTotal: T,
    snapshot: T
  ) extends AkkaPersistenceMetricsDef[T]

  val defaultConfig: AkkaPersistenceModule.Result = Impl(true, true, true, true, true)

  protected def fromMap(properties: Map[String, Boolean]): AkkaPersistenceModule.Config =
    Impl(
      recoveryTime = properties.getOrElse("recovery.time", defaultConfig.recoveryTime),
      recoveryTotal = properties.getOrElse("recovery.total", defaultConfig.recoveryTotal),
      persistentEvent = properties.getOrElse("persistent.event", defaultConfig.persistentEvent),
      persistentEventTotal = properties.getOrElse("persistent.event.total", defaultConfig.persistentEventTotal),
      snapshot = properties.getOrElse("snapshot", defaultConfig.snapshot)
    )

  override type All[T] = Metrics[T]

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
