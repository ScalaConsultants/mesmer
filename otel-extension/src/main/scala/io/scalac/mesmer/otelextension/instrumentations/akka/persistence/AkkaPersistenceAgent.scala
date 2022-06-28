package io.scalac.mesmer.otelextension.instrumentations.akka.persistence

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation

import io.scalac.mesmer.agent.util.dsl._
import io.scalac.mesmer.agent.util.dsl.matchers._

object AkkaPersistenceAgent {

  val replayingSnapshotOnRecoveryStart: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.ReplayingSnapshot"))(
      named("onRecoveryStart"),
      "akka.persistence.typed.ReplayingSnapshotOnRecoveryStartedAdvice"
    )

  val replayingEventsOnRecoveryComplete: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.ReplayingEvents"))(
      named("onRecoveryComplete"),
      "akka.persistence.typed.ReplayingEventsOnRecoveryCompleteAdvice"
    )
  val runningOnWriteSuccessInstrumentation: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.Running"))(
      named("onWriteSuccess"),
      "akka.persistence.typed.RunningOnWriteSuccessAdvice"
    )

  val runningOnWriteInitiatedInstrumentation: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.Running"))(
      named("onWriteInitiated"),
      "akka.persistence.typed.RunningOnWriteInitiatedAdvice"
    )

  val storingSnapshotonWriteInitiated: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.Running$StoringSnapshot"))(
      named("onSaveSnapshotResponse"),
      "akka.persistence.typed.StoringSnapshotOnSaveSnapshotResponseAdvice"
    )
//
//  lazy val recoveryTime: Agent = recoveryAgent
//
//  lazy val recoveryTotal: Agent = recoveryAgent
//
//  lazy val persistentEvent: Agent = eventWriteSuccessAgent
//
//  lazy val persistentEventTotal: Agent = eventWriteSuccessAgent
//
//  lazy val snapshot: Agent = snapshotLoadingAgent
//
//  private val recoveryAgent = {
//
//    val recoveryTag = "recovery"
//
//    /**
//     * Instrumentation to fire event on persistent actor recovery start
//     */
//    val recoveryStartedAgent =
//      instrument("akka.persistence.typed.internal.ReplayingSnapshot".fqcnWithTags(recoveryTag))
//        .visit(RecoveryStartedAdvice, "onRecoveryStart")
//
//    /**
//     * Instrumentation to fire event on persistent actor recovery complete
//     */
//    val recoveryCompletedAgent =
//      instrument("akka.persistence.typed.internal.ReplayingEvents".fqcnWithTags(recoveryTag))
//        .visit(RecoveryCompletedAdvice, "onRecoveryComplete")
//
//    Agent(recoveryStartedAgent, recoveryCompletedAgent)
//  }
//
//  /**
//   * Instrumentation to fire events on persistent event start and stop
//   */
//  private val eventWriteSuccessAgent =
//    Agent(
//      instrument("akka.persistence.typed.internal.Running".fqcnWithTags("persistent_event"))
//        .visit(PersistingEventSuccessAdvice, "onWriteSuccess")
//        .visit(JournalInteractionsAdvice, "onWriteInitiated")
//    )
//
//  /**
//   * Instrumentation to fire event when snapshot is stored
//   */
//  private val snapshotLoadingAgent =
//    Agent(
//      instrument("akka.persistence.typed.internal.Running$StoringSnapshot".fqcnWithTags("snapshot_created"))
//        .visit(StoringSnapshotAdvice, "onSaveSnapshotResponse")
//    )
}
