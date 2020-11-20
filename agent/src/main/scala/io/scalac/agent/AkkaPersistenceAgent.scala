package io.scalac.agent

import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._

object AkkaPersistenceAgent {

  private val recoveryStartedAgent = Agent { (agentBuilder, instrumentation) =>
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

  private val recoveryCompletedAgent = Agent { (agentBuilder, instrumentation) =>
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
