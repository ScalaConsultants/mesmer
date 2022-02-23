package io.scalac.mesmer.agent.akka.persistence

import org.slf4j.LoggerFactory

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.akka.persistence.impl._
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.akka.version26x
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.AkkaPersistenceModule

object AkkaPersistenceAgent
    extends InstrumentModuleFactory(AkkaPersistenceModule)
    with AkkaPersistenceModule.All[AkkaPersistenceModule.Jars[Version] => Agent] {

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Resulting agent and resulting configuration based on runtime properties
   */
  def agent(
    jars: AkkaPersistenceModule.AkkaPersistenceJars[Version]
  ): Agent = {

    val config = module.enabled

    // TODO can we introduce some foldable and/or functor/applicative to be able to map over those without manually checking every element here
    val recoveryTimeAgent         = if (config.recoveryTime) recoveryTime(jars) else Agent.empty
    val recoveryTotalAgent        = if (config.recoveryTotal) recoveryTotal(jars) else Agent.empty
    val persistentEventAgent      = if (config.persistentEvent) persistentEvent(jars) else Agent.empty
    val persistentEventTotalAgent = if (config.persistentEventTotal) persistentEventTotal(jars) else Agent.empty
    val snapshotAgent             = if (config.snapshot) snapshot(jars) else Agent.empty

    val resultantAgent =
      recoveryTimeAgent ++
        recoveryTotalAgent ++
        persistentEventAgent ++
        persistentEventTotalAgent ++
        snapshotAgent

    resultantAgent
  }

  private def ifSupported(agent: => Agent)(versions: AkkaPersistenceModule.Jars[Version]): Agent = {
    import versions._
    if (
      version26x.supports(akkaPersistence) && version26x.supports(akkaPersistenceTyped) && version26x
        .supports(akkaActor) && version26x.supports(akkaActorTyped)
    ) agent
    else Agent.empty
  }

  lazy val recoveryTime: AkkaPersistenceModule.Jars[Version] => Agent = ifSupported(recoveryAgent)

  lazy val recoveryTotal: AkkaPersistenceModule.Jars[Version] => Agent = ifSupported(recoveryAgent)

  lazy val persistentEvent: AkkaPersistenceModule.Jars[Version] => Agent = ifSupported(
    Agent(eventWriteSuccessInstrumentation)
  )

  lazy val persistentEventTotal: AkkaPersistenceModule.Jars[Version] => Agent = ifSupported(
    Agent(eventWriteSuccessInstrumentation)
  )

  lazy val snapshot: AkkaPersistenceModule.Jars[Version] => Agent = ifSupported(
    Agent(snapshotLoadingInstrumentation)
  )

  private[persistence] val logger = LoggerFactory.getLogger(AkkaPersistenceAgent.getClass)

  private val recoveryAgent = {

    val recoveryTag = "recovery"

    /**
     * Instrumentation to fire event on persistent actor recovery start
     */
    val recoveryStartedAgent =
      instrument("akka.persistence.typed.internal.ReplayingSnapshot".fqcnWithTags(recoveryTag))
        .intercept(RecoveryStartedInterceptor, "onRecoveryStart")

    /**
     * Instrumentation to fire event on persistent actor recovery complete
     */
    val recoveryCompletedAgent =
      instrument("akka.persistence.typed.internal.ReplayingEvents".fqcnWithTags(recoveryTag))
        .intercept(RecoveryCompletedInterceptor, "onRecoveryComplete")

    Agent(recoveryStartedAgent, recoveryCompletedAgent)
  }

  /**
   * Instrumentation to fire events on persistent event start and stop
   */
  private val eventWriteSuccessInstrumentation =
    instrument("akka.persistence.typed.internal.Running".fqcnWithTags("persistent_event"))
      .intercept(PersistingEventSuccessInterceptor, "onWriteSuccess")
      .intercept(JournalInteractionsInterceptor, "onWriteInitiated")

  /**
   * Instrumentation to fire event when snapshot is stored
   */
  private val snapshotLoadingInstrumentation =
    instrument("akka.persistence.typed.internal.Running$StoringSnapshot".fqcnWithTags("snapshot_created"))
      .intercept(StoringSnapshotInterceptor, "onSaveSnapshotResponse")
}
