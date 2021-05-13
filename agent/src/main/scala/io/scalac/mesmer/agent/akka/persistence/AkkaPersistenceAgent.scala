package io.scalac.mesmer.agent.akka.persistence

import org.slf4j.LoggerFactory
import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.support.ModulesSupport
import net.bytebuddy.pool.TypePool

object AkkaPersistenceAgent extends InstrumentModuleFactory {



  private[persistence] val logger = LoggerFactory.getLogger(AkkaPersistenceAgent.getClass)

  protected val supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaPersistenceTypedModule, ModulesSupport.akkaPersistenceTyped)

  private val recoveryStartedAgent =
    instrument("akka.persistence.typed.internal.ReplayingSnapshot")
      .intercept[RecoveryStartedInterceptor]("onRecoveryStart")

  private val recoveryCompletedAgent =
    instrument("akka.persistence.typed.internal.ReplayingEvents")
      .intercept[RecoveryCompletedInterceptor]("onRecoveryComplete")

  private val eventWriteSuccessInstrumentation =
    instrument("akka.persistence.typed.internal.Running")
      .intercept[PersistingEventSuccessInterceptor]("onWriteSuccess")
      .intercept[JournalInteractionsInterceptor]("onWriteInitiated")

  private val snapshotLoadingInstrumentation =
    instrument("akka.persistence.typed.internal.Running$StoringSnapshot")
      .intercept[StoringSnapshotInterceptor]("onSaveSnapshotResponse")

  val agent: Agent =
    Agent(
      recoveryStartedAgent,
      recoveryCompletedAgent,
      eventWriteSuccessInstrumentation,
      snapshotLoadingInstrumentation
    )
}
