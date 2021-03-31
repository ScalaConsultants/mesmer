package io.scalac.agent.akka.actor

import akka.actor.typed.Behavior

import io.scalac.agent.Agent
import io.scalac.agent.util.i13n._
import io.scalac.core.model._
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.Timestamp
import io.scalac.extension.actor.{ ActorCellDecorator, ActorCellMetrics }

object AkkaActorAgent extends InstrumentModuleFactory {

  protected final val supportedModules = SupportedModules(ModulesSupport.akkaActorModule, ModulesSupport.akkaActor)

  private val classicStashInstrumentation =
    instrument("akka.actor.StashSupport")
      .visit[ClassicStashInstrumentation](
        methods(
          "stash",
          "prepend",
          "unstash",
          method("unstashAll").takesArguments(1)
        )
      )

  private val typedStashInstrumentation =
    instrument("akka.actor.typed.internal.StashBufferImpl")
      .visit[TypedStashInstrumentation](
        methods(
          "stash",
          method("unstash").takesArguments[Behavior[_], Int, (_) => _]
        )
      )

  private val mailboxTimeTimestampInstrumentation =
    instrument("akka.dispatch.Envelope")
      .defineField[Timestamp](EnvelopeDecorator.TimestampVarName)

  private val mailboxTimeSendMessageInstrumentation =
    instrument("akka.actor.dungeon.Dispatch")
      .visit[ActorCellSendMessageInstrumentation](
        method("sendMessage").takesArgument(0, "akka.dispatch.Envelope")
      )

  private val mailboxTimeDequeueInstrumentation =
    instrument("akka.dispatch.Mailbox")
      .visit[MailboxDequeueInstrumentation]("dequeue")

  private val actorCellInstrumentation =
    instrument("akka.actor.ActorCell")
      .defineField[ActorCellMetrics](ActorCellDecorator.fieldName)
      .visit[ActorCellConstructorInstrumentation](constructor)
      .visit[ActorCellReceiveMessageInstrumentation]("receiveMessage")

  private val actorInstrumentation =
    instrument("akka.actor.Actor")
      .visit[ActorUnhandledInstrumentation]("unhandled")

  private val abstractSupervisionInstrumentation =
    instrument(
      hierarchy("akka.actor.typed.internal.AbstractSupervisor")
        .overrides("handleReceiveException")
    ).intercept[SupervisorHandleReceiveExceptionInstrumentation]("handleReceiveException")

  val agent: Agent = Agent(
    classicStashInstrumentation,
    typedStashInstrumentation,
    mailboxTimeTimestampInstrumentation,
    mailboxTimeSendMessageInstrumentation,
    mailboxTimeDequeueInstrumentation,
    actorCellInstrumentation,
    actorInstrumentation,
    abstractSupervisionInstrumentation
  )

}
