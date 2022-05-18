package io.scalac.mesmer.otelextension.instrumentations.akka.http

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers

object Connections {

  val connections: TypeInstrumentation = new TypeInstrumentation {
    val typeMatcher: ElementMatcher[TypeDescription] = ElementMatchers
      .named[TypeDescription]("akka.http.scaladsl.HttpExt")

    def transform(transformer: TypeTransformer): Unit =
      transformer.applyAdviceToMethod(
        ElementMatchers.named[MethodDescription]("bindAndHandle"),
        "io.scalac.mesmer.instrumentation.http.HttpExtConnectionAdvice"
      )
  }
}
