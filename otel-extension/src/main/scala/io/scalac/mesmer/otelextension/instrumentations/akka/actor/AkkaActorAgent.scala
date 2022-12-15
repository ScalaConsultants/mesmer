package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import net.bytebuddy.description.`type`.TypeDescription

import io.scalac.mesmer.agent.util.dsl._
import io.scalac.mesmer.agent.util.dsl.matchers._
import io.scalac.mesmer.agent.util.i13n.Advice
import io.scalac.mesmer.agent.util.i13n.Instrumentation
import io.scalac.mesmer.agent.util.i13n.Instrumentation._

object AkkaActorAgent {

  val actorMetricsExtension: TypeInstrumentation =
    Instrumentation(named("akka.actor.ActorSystemImpl"))
      .`with`(Advice(isConstructor, "akka.actor.impl.ActorMetricsExtensionAdvice"))

  val actorSystemConfig: TypeInstrumentation =
    Instrumentation(named("akka.actor.ActorSystemImpl"))
      .`with`(Advice(isConstructor, "akka.actor.impl.ClassicActorSystemProviderAdvice"))

  val actorCellInit: TypeInstrumentation = Instrumentation(named("akka.actor.ActorCell"))
    .`with`(Advice(isConstructor, "akka.actor.impl.ActorCellInitAdvice"))

  val actorCellReceived: TypeInstrumentation = Instrumentation(named("akka.actor.ActorCell"))
    .`with`(Advice(named("receiveMessage"), "akka.actor.impl.ActorCellReceivedAdvice"))

  val dispatchSendMessage: TypeInstrumentation = Instrumentation(named("akka.actor.dungeon.Dispatch"))
    .`with`(
      Advice(
        named("sendMessage").and(takesArgument(0, named("akka.dispatch.Envelope"))),
        "akka.actor.impl.DispatchSendMessageAdvice"
      )
    )

  val mailboxDequeue: TypeInstrumentation = Instrumentation(named("akka.dispatch.Mailbox"))
    .`with`(
      Advice(
        named("dequeue"),
        "akka.actor.impl.MailboxDequeueAdvice"
      )
    )

  val classicStashSupportStashAdvice: TypeInstrumentation =
    Instrumentation(named("akka.actor.StashSupport"))
      .`with`(
        Advice(
          named("stash"),
          "akka.actor.impl.StashSupportStashAdvice"
        )
      )

  val classicStashSupportPrependAdvice: TypeInstrumentation =
    Instrumentation(named("akka.actor.StashSupport"))
      .`with`(
        Advice(
          named("prepend"),
          "akka.actor.impl.StashSupportPrependAdvice"
        )
      )

  val typedStashBufferAdvice: TypeInstrumentation =
    Instrumentation(
      matchers
        .hasSuperType(named("akka.actor.typed.internal.StashBufferImpl"))
    ).`with`(
      Advice(
        named("stash"),
        "akka.actor.impl.typed.StashBufferImplStashAdvice"
      )
    )

  val typedAbstractSupervisorHandleReceiveExceptionAdvice: TypeInstrumentation =
    Instrumentation(
      matchers
        .hasSuperType(
          matchers
            .named("akka.actor.typed.internal.AbstractSupervisor")
        )
        .and(
          declaresMethod(
            matchers
              .named("handleReceiveException")
              .and(isOverriddenFrom(named("akka.actor.typed.internal.AbstractSupervisor")))
          )
        )
    ).`with`(
      Advice(
        named("handleReceiveException"),
        "akka.actor.impl.typed.SupervisorHandleReceiveExceptionAdvice"
      )
    )

  val actorUnhandledAdvice: TypeInstrumentation =
    Instrumentation(
      named("akka.actor.Actor")
    ).`with`(
      Advice(
        named("unhandled"),
        "akka.actor.impl.ActorUnhandledAdvice"
      )
    )

  // mailbox

  val abstractBoundedNodeQueueAdvice: TypeInstrumentation =
    Instrumentation(
      named("akka.dispatch.AbstractBoundedNodeQueue")
    ).`with`(
      Advice(
        named("add"),
        "akka.actor.impl.AbstractBoundedNodeQueueAddAdvice"
      )
    )

  val boundedQueueBasedMessageQueueConstructorAdvice: TypeInstrumentation =
    Instrumentation(
      matchers
        .hasSuperType[TypeDescription](named("akka.dispatch.BoundedQueueBasedMessageQueue"))
        .and[TypeDescription](
          hasSuperType[TypeDescription](named("java.util.concurrent.BlockingQueue"))
        )
        .and[TypeDescription](not[TypeDescription](isAbstract))
    ).`with`(
      Advice(
        isConstructor,
        "akka.actor.impl.BoundedQueueBasedMessageQueueConstructorAdvice"
      )
    )

  val boundedQueueBasedMessageQueueQueueAdvice: TypeInstrumentation =
    Instrumentation(
      matchers
        .hasSuperType[TypeDescription](named("akka.dispatch.BoundedQueueBasedMessageQueue"))
        .and[TypeDescription](
          hasSuperType[TypeDescription](named("java.util.concurrent.BlockingQueue"))
        )
        .and[TypeDescription](not[TypeDescription](isAbstract))
    ).`with`(
      Advice(
        named("queue"),
        "akka.actor.impl.BoundedQueueBasedMessageQueueQueueAdvice"
      )
    )

  val boundedMessageQueueSemanticsEnqueueAdvice: TypeInstrumentation = Instrumentation(
    matchers
      .hasSuperType(named("akka.dispatch.BoundedMessageQueueSemantics"))
      .and(not(isAbstract))
  ).`with`(
    Advice(
      named("enqueue"),
      "akka.actor.impl.BoundedMessageQueueSemanticsEnqueueAdvice"
    )
  )

  val actorCreatedAdvice: TypeInstrumentation = Instrumentation(
    matchers.named("akka.actor.LocalActorRefProvider")
  ).`with`(
    Advice(named("actorOf"), "akka.actor.impl.LocalActorRefProviderAdvice")
  )

  val mailboxSizeAdvice: TypeInstrumentation = Instrumentation(
    matchers.named("akka.actor.LocalActorRefProvider")
  ).`with`(
    Advice(named("actorOf"), "akka.actor.impl.MailboxSizeAdvice")
  )

}
