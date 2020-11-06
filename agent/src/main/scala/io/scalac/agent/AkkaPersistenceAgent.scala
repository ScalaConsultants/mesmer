package io.scalac.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.scaffold.TypeValidation
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers._

object AkkaPersistenceAgent {

  def install(): Unit = {
    val agent = ByteBuddyAgent.install

    val onRecoveryCompleteDescription: ElementMatcher.Junction[MethodDescription] =
      isMethod[MethodDescription].and(named("onRecoveryComplete"))
    val onRecoveryStartDescription: ElementMatcher.Junction[MethodDescription] =
      isMethod[MethodDescription].and(named("onRecoveryStart"))

    new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
      .`with`(AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly)
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.ReplayingSnapshot"))
      .transform {
        case (builder, _, _, _) =>
          builder.method(onRecoveryStartDescription).intercept(Advice.to(classOf[RecoveryStartedInterceptor]))
      }
      .installOn(agent)

    new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
      .`with`(AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly)
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)
      .`type`(named[TypeDescription]("akka.persistence.typed.internal.ReplayingEvents"))
      .transform {
        case (builder, _, _, _) =>
          builder.method(onRecoveryCompleteDescription).intercept(Advice.to(classOf[RecoveryCompletedInterceptor]))
      }
      .installOn(agent)

      // this is done eagerly to not affect processing time for the first incoming command by bytecode transformations.
      ClassLoader.getSystemClassLoader.loadClass("akka.persistence.typed.internal.ReplayingEvents")
      ClassLoader.getSystemClassLoader.loadClass("akka.persistence.typed.internal.ReplayingSnapshot")
  }
}
