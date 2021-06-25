package io.scalac.mesmer.agent.akka.persistence

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.akka.persistence.impl._
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.model.{SupportedModules, Version}
import io.scalac.mesmer.core.module.AkkaPersistenceModule
import io.scalac.mesmer.core.support.ModulesSupport
import org.slf4j.LoggerFactory

object AkkaPersistenceAgent
    extends InstrumentModuleFactory(AkkaPersistenceModule)
    with AkkaPersistenceModule.All[AkkaPersistenceModule.AkkaJar[Version] => Option[Agent]] {

  /**
   * @param config configuration of features that are wanted by the user
   * @param jars   versions of required jars to deduce which features can be enabled
   * @return Resulting agent and resulting configuration based on runtime properties
   */
  override protected def agent(
    config: AkkaPersistenceModule.AkkaPersistenceMetricsDef[Boolean],
    jars: AkkaPersistenceModule.Jars[Version]
  ): (Agent, AkkaPersistenceModule.AkkaPersistenceMetricsDef[Boolean]) = {

    val recoveryTimeAgent         = if (config.recoveryTime) recoveryTime(jars) else None
    val recoveryTotalAgent        = if (config.recoveryTotal) recoveryTime(jars) else None
    val persistentEventAgent      = if (config.persistentEvent) recoveryTime(jars) else None
    val persistentEventTotalAgent = if (config.persistentEventTotal) recoveryTime(jars) else None
    val snapshotAgent             = if (config.snapshot) recoveryTime(jars) else None

    val resultantAgent =
      recoveryTimeAgent.getOrElse(Agent.empty) ++
        recoveryTotalAgent.getOrElse(Agent.empty) ++
        persistentEventAgent.getOrElse(Agent.empty) ++
        persistentEventTotalAgent.getOrElse(Agent.empty) ++
        snapshotAgent.getOrElse(Agent.empty)

    val enabled = AkkaPersistenceModule.Impl(
      recoveryTime = recoveryTimeAgent.isDefined,
      recoveryTotal = recoveryTotalAgent.isDefined,
      persistentEvent = persistentEventAgent.isDefined,
      persistentEventTotal = persistentEventTotalAgent.isDefined,
      snapshot = snapshotAgent.isDefined
    )

    (resultantAgent, enabled)
  }

  lazy val recoveryTime: AkkaPersistenceModule.AkkaJar[Version] => Option[Agent] = _ => Some(recoveryAgent)

  lazy val recoveryTotal: AkkaPersistenceModule.AkkaJar[Version] => Option[Agent] = _ => Some(recoveryAgent)

  lazy val persistentEvent: AkkaPersistenceModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(Agent(eventWriteSuccessInstrumentation))

  lazy val persistentEventTotal: AkkaPersistenceModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(Agent(eventWriteSuccessInstrumentation))

  lazy val snapshot: AkkaPersistenceModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(Agent(snapshotLoadingInstrumentation))

  private[persistence] val logger = LoggerFactory.getLogger(AkkaPersistenceAgent.getClass)

  private val recoveryAgent = {

    val recoveryTag = "recovery"

    /**
     * Instrumentation to fire event on persistent actor recovery start
     */
    val recoveryStartedAgent =
      instrument("akka.persistence.typed.internal.ReplayingSnapshot".fqcnWithTags(recoveryTag))
        .visit(RecoveryStartedInterceptor, "onRecoveryStart")

    /**
     * Instrumentation to fire event on persistent actor recovery complete
     */
    val recoveryCompletedAgent =
      instrument("akka.persistence.typed.internal.ReplayingEvents".fqcnWithTags(recoveryTag))
        .visit(RecoveryCompletedInterceptor, "onRecoveryComplete")

    Agent(recoveryStartedAgent, recoveryCompletedAgent)
  }

  /**
   * Instrumentation to fire events on persistent event start and stop
   */
  private val eventWriteSuccessInstrumentation =
    instrument("akka.persistence.typed.internal.Running".fqcnWithTags("persistent_event"))
      .visit(PersistingEventSuccessInterceptor, "onWriteSuccess")
      .visit(JournalInteractionsInterceptor, "onWriteInitiated")

  /**
   * Instrumentation to fire event when snapshot is stored
   */
  private val snapshotLoadingInstrumentation =
    instrument("akka.persistence.typed.internal.Running$StoringSnapshot".fqcnWithTags("snapshot_created"))
      .visit(StoringSnapshotInterceptor, "onSaveSnapshotResponse")
}
