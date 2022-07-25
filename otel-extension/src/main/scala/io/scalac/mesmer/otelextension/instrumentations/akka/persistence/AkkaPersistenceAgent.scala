package io.scalac.mesmer.otelextension.instrumentations.akka.persistence
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.tooling.Utils
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.MemberSubstitution
import net.bytebuddy.description.method.MethodDescription

import io.scalac.mesmer.agent.util.dsl._
import io.scalac.mesmer.agent.util.dsl.matchers._
import io.scalac.mesmer.agent.util.i13n.Advice
import io.scalac.mesmer.agent.util.i13n.Instrumentation

object AkkaPersistenceAgent {

  val actorSystemPersistenceProvider: TypeInstrumentation =
    Instrumentation(named("akka.actor.ActorSystemImpl"))
      .`with`(Advice(isConstructor, "akka.persistence.typed.ActorSystemImplInitPersistenceContextProviderAdvice"))

  val replayingSnapshotOnRecoveryStart: TypeInstrumentation =
    Instrumentation(named("akka.persistence.typed.internal.ReplayingSnapshot")).`with`(
      Advice(
        named("onRecoveryStart"),
        "akka.persistence.typed.ReplayingSnapshotOnRecoveryStartedAdvice"
      )
    )

  val replayingEventsOnRecoveryComplete: TypeInstrumentation =
    Instrumentation(named("akka.persistence.typed.internal.ReplayingEvents")).`with`(
      Advice(
        named("onRecoveryComplete"),
        "akka.persistence.typed.ReplayingEventsOnRecoveryCompleteAdvice"
      )
    )
  val runningOnWriteSuccessInstrumentation: TypeInstrumentation =
    Instrumentation(named("akka.persistence.typed.internal.Running")).`with`(
      Advice(
        named("onWriteSuccess"),
        "akka.persistence.typed.RunningOnWriteSuccessAdvice"
      )
    )

  val runningOnWriteInitiatedInstrumentation: TypeInstrumentation =
    Instrumentation(named("akka.persistence.typed.internal.Running")).`with`(
      Advice(
        named("onWriteInitiated"),
        "akka.persistence.typed.RunningOnWriteInitiatedAdvice"
      )
    )

  val storingSnapshotOnWriteInitiated: TypeInstrumentation =
    Instrumentation(named("akka.persistence.typed.internal.Running$StoringSnapshot"))
      .`with`(Advice { (dynamic, _, _, _) =>
        dynamic
          .visit(
            MemberSubstitution
              .relaxed()
              .method(matchers.named("dummyContext"))
              .replaceWithMethod(matchers.named("context"))
              .on(matchers.named[MethodDescription]("onSaveSnapshotResponse"))
          )
      })
      .`with`(
        Advice(
          new AgentBuilder.Transformer.ForAdvice()
            // for some reason direct access result in compilation errors
            .include(classOf[Utils].getDeclaredMethod("getBootstrapProxy").invoke(null).asInstanceOf[ClassLoader])
            .include(Utils.getAgentClassLoader)
            .include(Utils.getExtensionsClassLoader)
            .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
            .advice(
              matchers.named[MethodDescription]("onSaveSnapshotResponse"),
              "akka.persistence.typed.StoringSnapshotOnSaveSnapshotResponseAdvice"
            )
        )
      )

  val abstractBehaviorSubstituteTest: TypeInstrumentation =
    Instrumentation(named("example.TestBehavior"))
      .`with`(Advice { (dynamic, _, _, _) =>
        dynamic
          .visit(
            MemberSubstitution
              .relaxed()
              .method(matchers.named("dummyContext"))
              .replaceWithMethod(matchers.named("context"))
              .on(matchers.named[MethodDescription]("onMessage"))
          )
      })
      .`with`(
        Advice(
          new AgentBuilder.Transformer.ForAdvice()
            // for some reason direct access result in compilation errors
            .include(classOf[Utils].getDeclaredMethod("getBootstrapProxy").invoke(null).asInstanceOf[ClassLoader])
            .include(Utils.getAgentClassLoader)
            .include(Utils.getExtensionsClassLoader)
            .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
            .advice(matchers.named[MethodDescription]("onMessage"), "akka.persistence.typed.TestAbstractBehaviorAdvice")
        )
      )

}
