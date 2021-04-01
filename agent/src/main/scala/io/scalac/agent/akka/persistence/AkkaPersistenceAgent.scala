package io.scalac.agent.akka.persistence

import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._
import org.slf4j.LoggerFactory

import io.scalac.agent.Agent
import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.AgentInstrumentation
import io.scalac.core.model.Module
import io.scalac.core.model.SupportedModules
import io.scalac.core.model.SupportedVersion
import io.scalac.core.model.Version

object AkkaPersistenceAgent {

  private[persistence] val logger = LoggerFactory.getLogger(AkkaPersistenceAgent.getClass)

  val defaultVersion: Version             = Version(2, 6, 8)
  val supportedVersions: SupportedVersion = SupportedVersion.majors("2") && SupportedVersion.minors("6")
  val moduleName: Module                  = Module("akka-persistence-typed")

  private val recoveryStartedAgent = AgentInstrumentation(
    "akka.persistence.typed.internal.ReplayingSnapshot",
    SupportedModules(moduleName, supportedVersions)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.ReplayingSnapshot"))
      .transform { case (builder, _, _, _) =>
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
      .transform { case (builder, _, _, _) =>
        builder
          .method(isMethod[MethodDescription].and(named("onRecoveryComplete")))
          .intercept(Advice.to(classOf[RecoveryCompletedInterceptor]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.persistence.typed.internal.ReplayingEvents")
  }

  private val eventWriteSuccessInstrumentation = AgentInstrumentation(
    "akka.persistence.typed.internal.Running",
    SupportedModules(moduleName, supportedVersions)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.Running"))
      .transform { case (builder, _, _, _) =>
        builder
          .method(isMethod[MethodDescription].and(named("onWriteSuccess")))
          .intercept(Advice.to(classOf[PersistingEventSuccessInterceptor]))
          .method(isMethod[MethodDescription].and(named("onWriteInitiated")))
          .intercept(Advice.to(classOf[JournalInteractionsInterceptor]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.persistence.typed.internal.Running")
  }

  private val snapshotLoadingInstrumentation = AgentInstrumentation(
    "akka.persistence.typed.internal.Running$StoringSnapshot",
    SupportedModules(moduleName, supportedVersions)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.Running$StoringSnapshot"))
      .transform { case (builder, _, _, _) =>
        builder
          .method(isMethod[MethodDescription].and(named("onSaveSnapshotResponse")))
          .intercept(Advice.to(classOf[StoringSnapshotInterceptor]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.persistence.typed.internal.Running$StoringSnapshot")
  }

  val agent: Agent =
    Agent(
      recoveryStartedAgent,
      recoveryCompletedAgent,
      eventWriteSuccessInstrumentation,
      snapshotLoadingInstrumentation
    )
}
