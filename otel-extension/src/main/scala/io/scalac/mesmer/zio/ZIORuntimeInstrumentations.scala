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
      .named[TypeDescription]("zio.Runtime$")

    override def transform(transformer: TypeTransformer): Unit =
      transformer
        .applyAdviceToMethod(
          ElementMatchers.named[MethodDescription]("apply"),
          "io.scalac.mesmer.zio.ZioRuntimeJavaAdvice2"
        )
  }

  val counterInstrumentation: TypeInstrumentation = new TypeInstrumentation {
    override def typeMatcher(): ElementMatcher[TypeDescription] =
      ElementMatchers.named[TypeDescription]("zio.metrics.Metric$")

    override def transform(transformer: TypeTransformer): Unit = transformer.applyAdviceToMethod(
      ElementMatchers.named[MethodDescription]("counter"),
      "io.scalac.mesmer.zio.ZIOCounterMetricAdvice"
    )
  }

  val gaugeInstrumentation: TypeInstrumentation = new TypeInstrumentation {
    override def typeMatcher(): ElementMatcher[TypeDescription] =
      ElementMatchers.named[TypeDescription]("zio.metrics.Metric$")

    override def transform(transformer: TypeTransformer): Unit = transformer.applyAdviceToMethod(
      ElementMatchers.named[MethodDescription]("gauge"),
      "io.scalac.mesmer.zio.ZIOGaugeMetricAdvice"
    )
  }

  val histogramInstrumentation: TypeInstrumentation = new TypeInstrumentation {
    override def typeMatcher(): ElementMatcher[TypeDescription] =
      ElementMatchers.named[TypeDescription]("zio.metrics.Metric$")

    override def transform(transformer: TypeTransformer): Unit = transformer.applyAdviceToMethod(
      ElementMatchers.named[MethodDescription]("histogram"),
      "io.scalac.mesmer.zio.ZIOHistogramMetricAdvice"
    )
  }
}
