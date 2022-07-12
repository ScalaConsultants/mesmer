package io.scalac.mesmer.otelextension.instrumentations.akka.persistence
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.tooling.Utils
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.MemberSubstitution
import net.bytebuddy.description.method.MethodDescription

import io.scalac.mesmer.agent.util.dsl._
import io.scalac.mesmer.agent.util.dsl.matchers._

object AkkaPersistenceAgent {

  val replayingSnapshotOnRecoveryStart: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.ReplayingSnapshot"))(
      named("onRecoveryStart"),
      "akka.persistence.typed.ReplayingSnapshotOnRecoveryStartedAdvice"
    )

  val replayingEventsOnRecoveryComplete: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.ReplayingEvents"))(
      named("onRecoveryComplete"),
      "akka.persistence.typed.ReplayingEventsOnRecoveryCompleteAdvice"
    )
  val runningOnWriteSuccessInstrumentation: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.Running"))(
      named("onWriteSuccess"),
      "akka.persistence.typed.RunningOnWriteSuccessAdvice"
    )

  val runningOnWriteInitiatedInstrumentation: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.Running"))(
      named("onWriteInitiated"),
      "akka.persistence.typed.RunningOnWriteInitiatedAdvice"
    )

  val storingSnapshotOnWriteInitiated: TypeInstrumentation =
    typeInstrumentation(named("akka.persistence.typed.internal.Running$StoringSnapshot")) { transformer =>
      // order of transformations matters

      transformer.applyTransformer { (dynamic, _, _, _) =>
        dynamic
          .visit(
            MemberSubstitution
              .relaxed()
              .method(matchers.named("dummyContext"))
              .replaceWithMethod(matchers.named("context"))
              .on(matchers.named[MethodDescription]("onSaveSnapshotResponse"))
          )
      }

      transformer.applyTransformer(
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

    }

  val abstractBehaviorSubstituteTest: TypeInstrumentation =
    typeInstrumentation(named("example.TestBehavior")) { transformer =>
      // order of transformations matters

      transformer.applyTransformer { (dynamic, _, _, _) =>
        dynamic
          .visit(
            MemberSubstitution
              .relaxed()
              .method(matchers.named("dummyContext"))
              .replaceWithMethod(matchers.named("context"))
              .on(matchers.named[MethodDescription]("onMessage"))
          )
      }

      transformer.applyTransformer(
        new AgentBuilder.Transformer.ForAdvice()
          // for some reason direct access result in compilation errors
          .include(classOf[Utils].getDeclaredMethod("getBootstrapProxy").invoke(null).asInstanceOf[ClassLoader])
          .include(Utils.getAgentClassLoader)
          .include(Utils.getExtensionsClassLoader)
          .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
          .advice(matchers.named[MethodDescription]("onMessage"), "akka.persistence.typed.TestAbstractBehaviorAdvice")
      )

    }
}
