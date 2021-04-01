package io.scalac.agent.akka.actor

import io.scalac.agent.Agent
import io.scalac.agent.util.i13n._
import io.scalac.core.actor.ActorCellDecorator
import io.scalac.core.actor.ActorCellMetrics
import io.scalac.core.model._
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.Timestamp

object AkkaActorAgent extends InstrumentModuleFactory {

  protected final val supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaActorModule, ModulesSupport.akkaActor)

  private val classicStashInstrumentationAgent = {

    val stashLogic =
      instrument("akka.actor.StashSupport")
        .visit[ClassicStashInstrumentationStash]("stash")
        .visit[ClassicStashInstrumentationPrepend]("prepend")

    val stashConstructor =
      instrument(hierarchy("akka.actor.StashSupport").concreteOnly)
        .visit[StashConstructorAdvice](constructor)

    Agent(stashLogic, stashConstructor)
  }

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

  private val stashBufferImplementation =
    instrument(hierarchy("akka.actor.typed.internal.StashBufferImpl"))
      .visit[StashBufferAdvice]("stash")

  val agent: Agent = Agent(
    mailboxTimeTimestampInstrumentation,
    mailboxTimeSendMessageInstrumentation,
    mailboxTimeDequeueInstrumentation,
    actorCellInstrumentation,
    actorInstrumentation,
    abstractSupervisionInstrumentation,
    stashBufferImplementation
  ) ++ classicStashInstrumentationAgent

}
