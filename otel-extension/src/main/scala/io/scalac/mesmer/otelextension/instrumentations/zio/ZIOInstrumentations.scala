package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers.isConstructor
import net.bytebuddy.matcher.ElementMatchers.named

import io.scalac.mesmer.agent.util.i13n.Advice
import io.scalac.mesmer.agent.util.i13n.Instrumentation

object ZIOInstrumentations {

  val executorMetricsAdvice: TypeInstrumentation =
    Instrumentation(named[TypeDescription]("zio.Executor")).`with`(
      Advice(
        isConstructor[MethodDescription],
        "io.scalac.mesmer.otelextension.instrumentations.zio.advice.ZIOExecutorAdvice"
      )
    )
}
