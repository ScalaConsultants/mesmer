package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._

import io.scalac.mesmer.agent.util.i13n.AdviceApplication.advice
import io.scalac.mesmer.agent.util.i13n.OtelTypeInstrumentation

object ZIOInstrumentations {

  val executorAdvice: TypeInstrumentation =
    OtelTypeInstrumentation
      .instrument(named[TypeDescription]("zio.Executor"))
      .using(
        advice(
          isConstructor[MethodDescription],
          "io.scalac.mesmer.otelextension.instrumentations.zio.advice.ZIOExecutorAdvice"
        )
      )
}
