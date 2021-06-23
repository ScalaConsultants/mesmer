package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }

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

  final case class AkkaPersistenceModuleConfig(
    recoveryTime: Boolean,
    recoveryTotal: Boolean,
    persistentEvent: Boolean,
    persistentEventTotal: Boolean,
    snapshot: Boolean
  ) extends AkkaPersistenceMetricsDef[Boolean]
      with ModuleConfig {

    lazy val enabled: Boolean = recoveryTime ||
      recoveryTotal ||
      persistentEvent ||
      persistentEventTotal ||
      snapshot
  }

  protected val defaultConfig: AkkaPersistenceModule.Result = AkkaPersistenceModuleConfig(true, true, true, true, true)

  protected def extractFromConfig(config: TypesafeConfig): Config = {
    val recoveryTime = config
      .tryValue("recovery-time")(_.getBoolean)
      .getOrElse(defaultConfig.recoveryTime)
    val recoveryTotal = config
      .tryValue("recovery-total")(_.getBoolean)
      .getOrElse(defaultConfig.recoveryTotal)
    val persistentEvent = config
      .tryValue("persistent-event")(_.getBoolean)
      .getOrElse(defaultConfig.persistentEvent)
    val persistentEventTotal = config
      .tryValue("persistent-event-total")(_.getBoolean)
      .getOrElse(defaultConfig.persistentEventTotal)
    val snapshot = config
      .tryValue("snapshot")(_.getBoolean)
      .getOrElse(defaultConfig.snapshot)

    AkkaPersistenceModuleConfig(recoveryTime, recoveryTotal, persistentEvent, persistentEventTotal, snapshot)
  }

  override type All[T] = Metrics[T]
}
