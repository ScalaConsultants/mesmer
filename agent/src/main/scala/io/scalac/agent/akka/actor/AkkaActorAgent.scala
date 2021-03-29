package io.scalac.agent.akka.actor

import akka.actor.typed.Behavior

import io.scalac.agent.Agent
import io.scalac.agent.util.i13n._
import io.scalac.core.model._
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.Timestamp
import io.scalac.extension.actor.{ ActorCellDecorator, ActorCellMetrics }

object AkkaActorAgent extends InstrumentModuleFactory {

  val moduleName: Module        = ModulesSupport.akkaActorModule
  val version: SupportedVersion = ModulesSupport.akkaActor
  val defaultVersion: Version   = Version(2, 6, 8)

  private val classicStashInstrumentation = instrument("akka.actor.StashSupport") { implicit b =>
    visit[ClassicStashInstrumentation](
      methods("stash", "prepend", "unstash")
        .or(method("unstashAll").takesArguments(1))
    )
  }

  private val typedStashInstrumentation = instrument("akka.actor.typed.internal.StashBufferImpl") { implicit b =>
    visit[TypedStashInstrumentation]("stash")
    visit[TypedUnstashInstrumentation](
      method("unstash")
        .takesArguments[Behavior[_], Int, (_) => _]
    )
  }

  private val mailboxTimeTimestampInstrumentation = instrument("akka.dispatch.Envelope") { implicit b =>
    defineField[Timestamp](EnvelopeDecorator.TimestampVarName)
  }

  private val mailboxTimeSendMessageInstrumentation = instrument("akka.actor.dungeon.Dispatch") { implicit b =>
    visit[ActorCellSendMessageInstrumentation](
      method("sendMessage")
        .takesArgument(0, "akka.dispatch.Envelope")
    )
  }

  private val mailboxTimeDequeueInstrumentation = instrument("akka.dispatch.Mailbox") { implicit b =>
    visit[MailboxDequeueInstrumentation]("dequeue")
  }

  private val actorCellInstrumentation = instrument("akka.actor.ActorCell") { implicit b =>
    defineField[ActorCellMetrics](ActorCellDecorator.fieldName)
    visit[ActorCellConstructorInstrumentation](constructor)
    visit[ActorCellReceiveMessageInstrumentation]("receiveMessage")
  }

  private val actorInstrumentation = instrument("akka.actor.Actor") { implicit b =>
    visit[ActorUnhandledInstrumentation]("unhandled")
  }

  private val abstractSupervisionInstrumentation = instrument(
    hierarchy("akka.actor.typed.internal.AbstractSupervisor")
      .overrides(method("handleReceiveException"))
  ) { implicit b =>
    intercept[SupervisorHandleReceiveExceptionInstrumentation]("handleReceiveException")
  }

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
