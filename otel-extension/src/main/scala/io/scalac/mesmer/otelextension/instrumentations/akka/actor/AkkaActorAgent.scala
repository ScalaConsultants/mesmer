package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import net.bytebuddy.description.`type`.TypeDescription

import io.scalac.mesmer.agent.util.dsl._

object AkkaActorAgent {

  val actorSystemConfig: TypeInstrumentation =
    typeInstrumentation(matchers.hasSuperType(matchers.named("akka.actor.ClassicActorSystemProvider")))(
      matchers.isConstructor,
      "akka.actor.impl.ClassicActorSystemProviderAdvice"
    )

  val actorCellInit: TypeInstrumentation =
    typeInstrumentation(matchers.named("akka.actor.ActorCell"))(
      matchers.isConstructor,
      "akka.actor.impl.ActorCellInitAdvice"
    )

  val actorCellReceived: TypeInstrumentation = typeInstrumentation(matchers.named("akka.actor.ActorCell"))(
    matchers.named("receiveMessage"),
    "akka.actor.impl.ActorCellReceivedAdvice"
  )

  val dispatchSendMessage: TypeInstrumentation = typeInstrumentation(matchers.named("akka.actor.dungeon.Dispatch"))(
    matchers.named("sendMessage").and(matchers.takesArgument(0, matchers.named("akka.dispatch.Envelope"))),
    "akka.actor.impl.DispatchSendMessageAdvice"
  )

  val mailboxDequeue: TypeInstrumentation = typeInstrumentation(matchers.named("akka.dispatch.Mailbox"))(
    matchers.named("dequeue"),
    "akka.actor.impl.MailboxDequeueAdvice"
  )

  val classicStashSupportStashAdvice: TypeInstrumentation =
    typeInstrumentation(matchers.named("akka.actor.StashSupport"))(
      matchers.named("stash"),
      "akka.actor.impl.StashSupportStashAdvice"
    )

  val classicStashSupportPrependAdvice: TypeInstrumentation =
    typeInstrumentation(matchers.named("akka.actor.StashSupport"))(
      matchers.named("prepend"),
      "akka.actor.impl.StashSupportPrependAdvice"
    )

  val typedStashBufferAdvice: TypeInstrumentation =
    typeInstrumentation(
      matchers
        .hasSuperType(matchers.named("akka.actor.typed.internal.StashBufferImpl"))
    )(
      matchers.named("stash"),
      "akka.actor.impl.typed.StashBufferImplStashAdvice"
    )

  val typedAbstractSupervisorHandleReceiveExceptionAdvice: TypeInstrumentation =
    typeInstrumentation(
      matchers
        .hasSuperType(
          matchers
            .named("akka.actor.typed.internal.AbstractSupervisor")
        )
        .and(
          matchers.declaresMethod(
            matchers
              .named("handleReceiveException")
              .and(matchers.isOverriddenFrom(matchers.named("akka.actor.typed.internal.AbstractSupervisor")))
          )
        )
    )(
      matchers.named("handleReceiveException"),
      "akka.actor.impl.typed.SupervisorHandleReceiveExceptionAdvice"
    )

  val actorUnhandledAdvice: TypeInstrumentation =
    typeInstrumentation(
      matchers.named("akka.actor.Actor")
    )(
      matchers.named("unhandled"),
      "akka.actor.impl.ActorUnhandledAdvice"
    )

  // mailbox

  val abstractBoundedNodeQueueAdvice: TypeInstrumentation =
    typeInstrumentation(
      matchers.named("akka.dispatch.AbstractBoundedNodeQueue")
    )(
      matchers.named("add"),
      "akka.actor.impl.AbstractBoundedNodeQueueAddAdvice"
    )

  val boundedQueueBasedMessageQueueConstructorAdvice: TypeInstrumentation =
    typeInstrumentation(
      matchers
        .hasSuperType[TypeDescription](matchers.named("akka.dispatch.BoundedQueueBasedMessageQueue"))
        .and[TypeDescription](
          matchers.hasSuperType[TypeDescription](matchers.named("java.util.concurrent.BlockingQueue"))
        )
        .and[TypeDescription](matchers.not[TypeDescription](matchers.isAbstract))
    )(
      matchers.isConstructor,
      "akka.actor.impl.BoundedQueueBasedMessageQueueConstructorAdvice"
    )

  val boundedQueueBasedMessageQueueQueueAdvice: TypeInstrumentation =
    typeInstrumentation(
      matchers
        .hasSuperType[TypeDescription](matchers.named("akka.dispatch.BoundedQueueBasedMessageQueue"))
        .and[TypeDescription](
          matchers.hasSuperType[TypeDescription](matchers.named("java.util.concurrent.BlockingQueue"))
        )
        .and[TypeDescription](matchers.not[TypeDescription](matchers.isAbstract))
    )(
      matchers.named("queue"),
      "akka.actor.impl.BoundedQueueBasedMessageQueueQueueAdvice"
    )

  val boundedMessageQueueSemanticsEnqueueAdvice: TypeInstrumentation = typeInstrumentation(
    matchers
      .hasSuperType(matchers.named("akka.dispatch.BoundedMessageQueueSemantics"))
      .and(matchers.not(matchers.isAbstract))
  )(
    matchers.named("enqueue"),
    "akka.actor.impl.BoundedMessageQueueSemanticsEnqueueAdvice"
  )

}
