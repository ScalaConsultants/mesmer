package io.scalac.agent

import java.lang.instrument.Instrumentation

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.scaffold.TypeValidation
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers._

object AkkaPersistenceAgent {

  private final val ReplayingEventsClassName   = "akka.persistence.typed.internal.ReplayingEvents"
  private final val ReplayingSnapshotClassName = "akka.persistence.typed.internal.ReplayingSnapshot"

  private val onRecoveryCompleteMethod: ElementMatcher.Junction[MethodDescription] =
    isMethod[MethodDescription].and(named("onRecoveryComplete"))
  private val onRecoveryStartMethod: ElementMatcher.Junction[MethodDescription] =
    isMethod[MethodDescription].and(named("onRecoveryStart"))

  private def builder =
    new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
      .`with`(AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly)
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)

  def install(on: Instrumentation): Unit = {
    builder
      .`type`(named[TypeDescription](ReplayingSnapshotClassName))
      .transform {
        case (builder, _, _, _) =>
          builder.method(onRecoveryStartMethod).intercept(Advice.to(classOf[RecoveryStartedInterceptor]))
      }
      .installOn(on)

    builder
      .`type`(named[TypeDescription](ReplayingEventsClassName))
      .transform {
        case (builder, _, _, _) =>
          builder.method(onRecoveryCompleteMethod).intercept(Advice.to(classOf[RecoveryCompletedInterceptor]))
      }
      .installOn(on)
  }

  // can be done eagerly to not affect processing time for the first incoming command by bytecode transformations.
  def transformEagerly() = {
    ClassLoader.getSystemClassLoader.loadClass(ReplayingEventsClassName)
    ClassLoader.getSystemClassLoader.loadClass(ReplayingSnapshotClassName)
  }
}
