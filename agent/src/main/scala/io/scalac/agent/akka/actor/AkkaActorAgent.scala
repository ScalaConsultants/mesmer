package io.scalac.agent.akka.actor

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{ Agent, AgentInstrumentation }
import io.scalac.core.actor.{ ActorCellDecorator, ActorCellMetrics }
import io.scalac.core.model._
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.Timestamp
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._

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
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[ClassicStashInstrumentationStash])
                .on(
                  named[MethodDescription]("stash")
                )
            )
            .visit(
              Advice
                .to(classOf[ClassicStashInstrumentationPrepend])
                .on(named[MethodDescription]("prepend"))
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
              ActorCellDecorator.fieldName,
              classOf[ActorCellMetrics]
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

  private val stashBufferImplementation = {
    val stashBufferImpl = "akka.actor.typed.internal.StashBufferImpl"
    AgentInstrumentation(
      stashBufferImpl,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(
          hasSuperType[TypeDescription](named[TypeDescription](stashBufferImpl))
        )
        .transform { (builder, _, _, _) =>
          builder
            .method(named[MethodDescription]("stash"))
            .intercept(Advice.to(classOf[StashBufferAdvice]))
        }
        .installOn(instrumentation)
      LoadingResult(stashBufferImpl)
    }
  }

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
