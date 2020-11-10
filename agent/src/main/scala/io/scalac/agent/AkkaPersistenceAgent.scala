package io.scalac.agent

import java.lang.instrument.Instrumentation

import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers._

trait AkkaPersistenceAgent extends Agent {

  private final val ReplayingEventsClassName =
    "akka.persistence.typed.internal.ReplayingEvents"
  private final val ReplayingSnapshotClassName =
    "akka.persistence.typed.internal.ReplayingSnapshot"

  private val onRecoveryCompleteMethod
    : ElementMatcher.Junction[MethodDescription] =
    isMethod[MethodDescription].and(named("onRecoveryComplete"))
  private val onRecoveryStartMethod
    : ElementMatcher.Junction[MethodDescription] =
    isMethod[MethodDescription].and(named("onRecoveryStart"))

  override abstract def installOn(instrumentation: Instrumentation): Unit = {
    agentBuilder
      .`type`(named[TypeDescription](ReplayingSnapshotClassName))
      .transform {
        case (builder, _, _, _) =>
          builder
            .method(onRecoveryStartMethod)
            .intercept(Advice.to(classOf[RecoveryStartedInterceptor]))
      }
      .installOn(instrumentation)

    agentBuilder
      .`type`(named[TypeDescription](ReplayingEventsClassName))
      .transform {
        case (builder, _, _, _) =>
          builder
            .method(onRecoveryCompleteMethod)
            .intercept(Advice.to(classOf[RecoveryCompletedInterceptor]))
      }
      .installOn(instrumentation)
    super.installOn(instrumentation)
  }

  // can be done eagerly to not affect processing time for the first incoming command by bytecode transformations.
  override abstract def transformEagerly() = {
    ClassLoader.getSystemClassLoader.loadClass(ReplayingEventsClassName)
    ClassLoader.getSystemClassLoader.loadClass(ReplayingSnapshotClassName)
    super.transformEagerly()
  }
}
