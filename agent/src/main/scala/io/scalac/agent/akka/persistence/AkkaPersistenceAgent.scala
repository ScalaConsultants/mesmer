package io.scalac.agent.akka.persistence

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.model._
import io.scalac.agent.{ Agent, AgentInstrumentation }
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._
import org.slf4j.LoggerFactory

object AkkaPersistenceAgent {

  private[persistence] val logger = LoggerFactory.getLogger(AkkaPersistenceAgent.getClass)

  val defaultVersion    = Version(2, 6, 8)
  val supportedVersions = SupportedVersion.majors("2") && SupportedVersion.minors("6")
  val moduleName        = Module("akka-persistence-typed")

  private val recoveryStartedAgent = AgentInstrumentation(
    "akka.persistence.typed.internal.ReplayingSnapshot",
    SupportedModules(moduleName, supportedVersions)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.ReplayingSnapshot"))
      .transform {
        case (builder, _, _, _) =>
          builder
            .method(isMethod[MethodDescription].and(named("onRecoveryStart")))
            .intercept(Advice.to(classOf[RecoveryStartedInterceptor]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.persistence.typed.internal.ReplayingSnapshot")
  }

  private val recoveryCompletedAgent = AgentInstrumentation(
    "akka.persistence.typed.internal.ReplayingEvents",
    SupportedModules(moduleName, supportedVersions)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.ReplayingEvents"))
      .transform {
        case (builder, _, _, _) =>
          builder
            .method(isMethod[MethodDescription].and(named("onRecoveryComplete")))
            .intercept(Advice.to(classOf[RecoveryCompletedInterceptor]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.persistence.typed.internal.ReplayingEvents")
  }

  private val eventSourcesConstuctor = AgentInstrumentation(
    "akka.persistence.typed.internal.EventSourcedBehaviorImpl",
    SupportedModules(moduleName, supportedVersions)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.EventSourcedBehaviorImpl"))
      .transform {
        case (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[SnapshotAdvice])
                .on(isConstructor[MethodDescription].and(hasParameters[MethodDescription](any())))
            )
      }
      .installOn(instrumentation)
    LoadingResult("akka.persistence.typed.internal.EventSourcedBehaviorImpl")
  }

  private val eventWriteSuccessInstrumentation = AgentInstrumentation(
    "akka.persistence.typed.internal.Running",
    SupportedModules(moduleName, supportedVersions)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.Running"))
      .transform {
        case (builder, _, _, _) =>
          builder
            .method(isMethod[MethodDescription].and(named("onWriteSuccess")))
            .intercept(Advice.to(classOf[PersistingEventSuccessInterceptor]))
            .method(isMethod[MethodDescription].and(named("onWriteInitiated")))
            .intercept(Advice.to(classOf[JournalInteractionsInterceptor]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.persistence.typed.internal.Running")
  }

  val agent =
    Agent(recoveryStartedAgent, recoveryCompletedAgent, eventSourcesConstuctor, eventWriteSuccessInstrumentation)
}
