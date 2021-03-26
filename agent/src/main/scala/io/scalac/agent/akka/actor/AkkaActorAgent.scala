package io.scalac.agent.akka.actor

import akka.actor.typed.Behavior
import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{ Agent, AgentInstrumentation }
import io.scalac.core.model._
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.Timestamp
import io.scalac.extension.actor.{ ActorCountsDecorators, ActorTimesDecorators }
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong, AtomicReference }

object AkkaActorAgent {

  val moduleName: Module        = ModulesSupport.akkaActorModule
  val defaultVersion: Version   = Version(2, 6, 8)
  val version: SupportedVersion = ModulesSupport.akkaActor

  private val classicStashInstrumentationAgent = {
    val targetClassName = "akka.actor.StashSupport"
    val stashLogic = AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(
          named[TypeDescription](targetClassName)
        )
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[ClassicStashInstrumentation])
                .on(
                  named[MethodDescription]("stash")
                    .or[MethodDescription](named[MethodDescription]("prepend"))
                    .or[MethodDescription](named[MethodDescription]("unstash"))
                    .or[MethodDescription](
                      named[MethodDescription]("unstashAll")
                        .and[MethodDescription](takesArguments[MethodDescription](1))
                    )
                    .or[MethodDescription](named[MethodDescription]("clearStash"))
                )
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
    val stashConstructor = AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(
          hasSuperType[TypeDescription](named[TypeDescription](targetClassName))
            .and[TypeDescription](not[TypeDescription](isAbstract[TypeDescription]))
        )
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[StashConstructorAdvice])
                .on(isConstructor[MethodDescription])
            )

        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
    Agent(stashLogic, stashConstructor)
  }

  private val typedStashInstrumentation = {
    val targetClassName = "akka.actor.typed.internal.StashBufferImpl"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[TypedStashInstrumentation])
                .on(named[MethodDescription]("stash"))
            )
            .visit(
              Advice
                .to(classOf[TypedUnstashInstrumentation])
                .on(
                  named[MethodDescription]("unstash")
                    // since there're two `unstash` methods, we need to specify parameter types
                    .and[MethodDescription](
                      takesArguments[MethodDescription](classOf[Behavior[_]], classOf[Int], classOf[(_) => _])
                    )
                )
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val mailboxTimeTimestampInstrumentation = {
    val targetClassName = "akka.dispatch.Envelope"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform((builder, _, _, _) => builder.defineField(EnvelopeDecorator.TimestampVarName, classOf[Timestamp]))
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val mailboxTimeSendMessageInstrumentation = {
    val targetClassName = "akka.actor.dungeon.Dispatch"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[ActorCellSendMessageInstrumentation])
                .on(
                  named[MethodDescription]("sendMessage")
                    .and[MethodDescription](
                      takesArgument[MethodDescription](0, named[TypeDescription]("akka.dispatch.Envelope"))
                    )
                )
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val mailboxTimeDequeueInstrumentation = {
    val targetClassName = "akka.dispatch.Mailbox"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[MailboxDequeueInstrumentation])
                .on(named[MethodDescription]("dequeue"))
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val actorCellInstrumentation = {
    val targetClassName = "akka.actor.ActorCell"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .defineField(
              ActorTimesDecorators.MailboxTime.fieldName,
              classOf[ActorTimesDecorators.FieldType]
            )
            .defineField(
              ActorTimesDecorators.ProcessingTime.fieldName,
              classOf[ActorTimesDecorators.FieldType]
            )
            .defineField(
              ActorTimesDecorators.ProcessingTimeSupport.fieldName,
              classOf[AtomicReference[Timestamp]]
            )
            .defineField(
              ActorCountsDecorators.Received.fieldName,
              classOf[AtomicLong]
            )
            .defineField(
              ActorCountsDecorators.Unhandled.fieldName,
              classOf[AtomicLong]
            )
            .defineField(
              ActorCountsDecorators.Failed.fieldName,
              classOf[AtomicLong]
            )
            .defineField(
              ActorCountsDecorators.FailHandled.fieldName,
              classOf[AtomicBoolean]
            )
            .defineField(
              ActorCountsDecorators.Sent.fieldName,
              classOf[AtomicLong]
            )
            .defineField(
              ActorCountsDecorators.Stash.fieldName,
              classOf[AtomicLong]
            )
            .visit(
              Advice
                .to(classOf[ActorCellConstructorInstrumentation])
                .on(isConstructor[MethodDescription])
            )
            .visit(
              Advice
                .to(classOf[ActorCellReceiveMessageInstrumentation])
                .on(named[MethodDescription]("receiveMessage"))
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val actorInstrumentation = {
    val targetClassName = "akka.actor.Actor"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[ActorUnhandledInstrumentation])
                .on(named[MethodDescription]("unhandled"))
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val abstractSupervisionInstrumentation = {
    val abstractSupervisor = "akka.actor.typed.internal.AbstractSupervisor"
    AgentInstrumentation(
      abstractSupervisor,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(
          hasSuperType[TypeDescription](named[TypeDescription](abstractSupervisor))
            .and[TypeDescription](
              declaresMethod[TypeDescription](
                named[MethodDescription]("handleReceiveException")
                  .and[MethodDescription](
                    isOverriddenFrom[MethodDescription](named[TypeDescription](abstractSupervisor))
                  )
              )
            )
        )
        .transform { (builder, _, _, _) =>
          builder
            .method(named[MethodDescription]("handleReceiveException"))
            .intercept(Advice.to(classOf[SupervisorHandleReceiveExceptionInstrumentation]))
        }
        .installOn(instrumentation)
      LoadingResult(abstractSupervisor)
    }
  }

  val agent: Agent = Agent(
    mailboxTimeTimestampInstrumentation,
    mailboxTimeSendMessageInstrumentation,
    mailboxTimeDequeueInstrumentation,
    actorCellInstrumentation,
    actorInstrumentation,
    abstractSupervisionInstrumentation
  ) ++ classicStashInstrumentationAgent

}
