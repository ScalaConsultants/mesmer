package io.scalac.agent.akka.persistence

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.model._
import io.scalac.agent.{ Agent, AgentInstrumentation }
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers.{ isMethod, named }
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

  val agent = Agent(recoveryStartedAgent, recoveryCompletedAgent)
}
