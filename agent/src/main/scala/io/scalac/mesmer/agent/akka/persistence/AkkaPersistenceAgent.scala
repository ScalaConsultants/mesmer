package io.scalac.mesmer.agent.akka.persistence

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.akka.persistence.impl._
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.module.AkkaPersistenceModule
import io.scalac.mesmer.core.support.ModulesSupport
import org.slf4j.LoggerFactory

object AkkaPersistenceAgent
    extends InstrumentModuleFactory(AkkaPersistenceModule)
    with AkkaPersistenceModule.All[Agent] {

  override def agent(config: AkkaPersistenceModule.AkkaPersistenceMetricsDef[Boolean]): Agent =
    Agent.empty ++
      (if (config.recoveryTime) recoveryTime else Agent.empty) ++
      (if (config.recoveryTotal) recoveryTotal else Agent.empty) ++
      (if (config.persistentEvent) persistentEvent else Agent.empty) ++
      (if (config.persistentEventTotal) persistentEventTotal else Agent.empty) ++
      (if (config.snapshot) snapshot else Agent.empty)

  lazy val recoveryTime: Agent = recoveryAgent

  lazy val recoveryTotal: Agent = recoveryAgent

  lazy val persistentEvent: Agent = Agent(eventWriteSuccessInstrumentation)

  lazy val persistentEventTotal: Agent = Agent(eventWriteSuccessInstrumentation)

  lazy val snapshot: Agent = Agent(snapshotLoadingInstrumentation)

  private[persistence] val logger = LoggerFactory.getLogger(AkkaPersistenceAgent.getClass)

  protected val supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaPersistenceTypedModule, ModulesSupport.akkaPersistenceTyped)

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
