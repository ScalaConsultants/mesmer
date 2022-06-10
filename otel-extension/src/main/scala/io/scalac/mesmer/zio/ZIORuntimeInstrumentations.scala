package io.scalac.mesmer.zio

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers

object ZIORuntimeInstrumentations {

  val runMethodInstrumentation: TypeInstrumentation = new TypeInstrumentation {
    override def typeMatcher(): ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("zio.Runtime")

    override def transform(transformer: TypeTransformer): Unit =
      transformer
        .applyAdviceToMethod(
          // it's easier for now to attach to unsafeRun but in general both unsafeRunAsync and unsafeRunSync should be instrumented
          ElementMatchers.named[MethodDescription]("unsafeRun"),
          "io.scalac.mesmer.zio.ZioRuntimeJavaAdvice"
        )
  }
}
