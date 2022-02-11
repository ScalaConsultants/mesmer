package io.scalac.mesmer.agent.akka.persistence

import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.akka.persistence.impl._
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.akka.version26x
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.AkkaPersistenceModule

object AkkaPersistenceAgent
    extends InstrumentModuleFactory(AkkaPersistenceModule)
    with AkkaPersistenceModule.All[AkkaPersistenceModule.Jars[Version] => Option[Agent]] {

  private[persistence] val logger = LoggerFactory.getLogger(AkkaPersistenceAgent.getClass)

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Resulting agent and resulting configuration based on runtime properties
   */
  override protected def agent(
    config: AkkaPersistenceModule.AkkaPersistenceMetricsDef[Boolean],
    jars: AkkaPersistenceModule.AkkaPersistenceJars[Version]
  ): (Agent, AkkaPersistenceModule.AkkaPersistenceMetricsDef[Boolean]) = {

    val recoveryTimeAgent         = if (config.recoveryTime) recoveryTime(jars) else None
    val recoveryTotalAgent        = if (config.recoveryTotal) recoveryTotal(jars) else None
    val persistentEventAgent      = if (config.persistentEvent) persistentEvent(jars) else None
    val persistentEventTotalAgent = if (config.persistentEventTotal) persistentEventTotal(jars) else None
    val snapshotAgent             = if (config.snapshot) snapshot(jars) else None

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

  def agent(config: Config): Agent = {
    def orEmpty(condition: Boolean, agent: Agent): Agent = if (condition) agent else Agent.empty

    val configuration = module.enabled(config)

    orEmpty(configuration.recoveryTotal, recoveryAgent) ++
    orEmpty(configuration.recoveryTime, recoveryAgent) ++
    orEmpty(configuration.persistentEvent, eventWriteSuccessAgent) ++
    orEmpty(configuration.persistentEventTotal, eventWriteSuccessAgent) ++
    orEmpty(configuration.snapshot, snapshotLoadingAgent)
  }

  private def ifSupported(agent: => Agent)(versions: AkkaPersistenceModule.Jars[Version]): Option[Agent] = {
    import versions._
    if (
      version26x.supports(akkaPersistence) && version26x.supports(akkaPersistenceTyped) && version26x
        .supports(akkaActor) && version26x.supports(akkaActorTyped)
    )
      Some(agent)
    else None
  }

  lazy val recoveryTime: AkkaPersistenceModule.Jars[Version] => Option[Agent] = ifSupported(recoveryAgent)

  lazy val recoveryTotal: AkkaPersistenceModule.Jars[Version] => Option[Agent] = ifSupported(recoveryAgent)

  lazy val persistentEvent: AkkaPersistenceModule.Jars[Version] => Option[Agent] = ifSupported(eventWriteSuccessAgent)

  lazy val persistentEventTotal: AkkaPersistenceModule.Jars[Version] => Option[Agent] =
    ifSupported(eventWriteSuccessAgent)

  lazy val snapshot: AkkaPersistenceModule.Jars[Version] => Option[Agent] = ifSupported(snapshotLoadingAgent)

  private val recoveryAgent = {

    val recoveryTag = "recovery"

    /**
     * Instrumentation to fire event on persistent actor recovery start
     */
    val recoveryStartedAgent =
      instrument("akka.persistence.typed.internal.ReplayingSnapshot".fqcnWithTags(recoveryTag))
        .visit(RecoveryStartedAdvice, "onRecoveryStart")

    /**
     * Instrumentation to fire event on persistent actor recovery complete
     */
    val recoveryCompletedAgent =
      instrument("akka.persistence.typed.internal.ReplayingEvents".fqcnWithTags(recoveryTag))
        .visit(RecoveryCompletedAdvice, "onRecoveryComplete")

    Agent(recoveryStartedAgent, recoveryCompletedAgent)
  }

  /**
   * Instrumentation to fire events on persistent event start and stop
   */
  private val eventWriteSuccessAgent =
    Agent(
      instrument("akka.persistence.typed.internal.Running".fqcnWithTags("persistent_event"))
        .visit(PersistingEventSuccessAdvice, "onWriteSuccess")
        .visit(JournalInteractionsAdvice, "onWriteInitiated")
    )

  /**
   * Instrumentation to fire event when snapshot is stored
   */
  private val snapshotLoadingAgent =
    Agent(
      instrument("akka.persistence.typed.internal.Running$StoringSnapshot".fqcnWithTags("snapshot_created"))
        .visit(StoringSnapshotAdvice, "onSaveSnapshotResponse")
    )
}
