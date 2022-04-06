package io.scalac.mesmer.otelextension.instrumentations.akka.persistence

import org.slf4j.LoggerFactory

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.module.AkkaPersistenceModule
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl._

object AkkaPersistenceAgent
    extends InstrumentModuleFactory(AkkaPersistenceModule)
    with AkkaPersistenceModule.All[Agent] {

  private[persistence] val logger = LoggerFactory.getLogger(AkkaPersistenceAgent.getClass)

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Resulting agent and resulting configuration based on runtime properties
   */
  def agent: Agent = {

    val config = module.enabled

    List(
      recoveryTime.onCondition(config.recoveryTime),
      recoveryTotal.onCondition(config.recoveryTotal),
      persistentEvent.onCondition(config.persistentEvent),
      persistentEventTotal.onCondition(config.persistentEventTotal),
      snapshot.onCondition(config.snapshot)
    ).reduce(_ ++ _)
  }

  lazy val recoveryTime: Agent = recoveryAgent

  lazy val recoveryTotal: Agent = recoveryAgent

  lazy val persistentEvent: Agent = eventWriteSuccessAgent

  lazy val persistentEventTotal: Agent = eventWriteSuccessAgent

  lazy val snapshot: Agent = snapshotLoadingAgent

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
