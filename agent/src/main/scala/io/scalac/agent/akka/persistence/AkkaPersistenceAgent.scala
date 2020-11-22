package io.scalac.agent.akka.persistence

import io.scalac.agent.Agent
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers.{ isMethod, named }
import org.slf4j.LoggerFactory

object AkkaPersistenceAgent {

  private[persistence] val logger = LoggerFactory.getLogger(AkkaPersistenceAgent.getClass)

  val defaultVersion    = "2.6.8"
  val supportedVersions = Seq(defaultVersion)
  val moduleName        = "akka-persistence-typed"

  private val recoveryStartedAgent = Agent { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.ReplayingSnapshot"))
      .transform {
        case (builder, _, _, _) =>
          builder
            .method(isMethod[MethodDescription].and(named("onRecoveryStart")))
            .intercept(Advice.to(classOf[RecoveryStartedInterceptor]))
      }
      .installOn(instrumentation)
    List("akka.persistence.typed.internal.ReplayingSnapshot")
  }

  private val recoveryCompletedAgent = Agent { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.ReplayingEvents"))
      .transform {
        case (builder, _, _, _) =>
          builder
            .method(isMethod[MethodDescription].and(named("onRecoveryStart")))
            .intercept(Advice.to(classOf[RecoveryCompletedInterceptor]))
      }
      .installOn(instrumentation)
    List("akka.persistence.typed.internal.ReplayingEvents")
  }

  val agent = recoveryStartedAgent ++ recoveryCompletedAgent
}
