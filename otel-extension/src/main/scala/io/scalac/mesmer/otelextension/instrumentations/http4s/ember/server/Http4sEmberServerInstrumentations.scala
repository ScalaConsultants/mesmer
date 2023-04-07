package io.scalac.mesmer.otelextension.instrumentations.http4s.ember.server

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.scalac.mesmer.agent.util.dsl.matchers.isConstructor
import io.scalac.mesmer.agent.util.dsl.matchers.named
import io.scalac.mesmer.agent.util.i13n.Advice
import io.scalac.mesmer.agent.util.i13n.Instrumentation
import net.bytebuddy.description.method.MethodDescription

object Http4sEmberServerInstrumentations {

  val serverHelpersRunApp: TypeInstrumentation =
    Instrumentation(named("org.http4s.ember.server.internal.ServerHelpers$"))
      .`with`(
        Advice(
          named("runApp"),
          "io.scalac.mesmer.otelextension.instrumentations.http4s.ember.server.advice.ServerHelpersRunAppAdvice"
        )
      )
}
