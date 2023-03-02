package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers.isConstructor
import net.bytebuddy.matcher.ElementMatchers.named
import net.bytebuddy.matcher.ElementMatchers.namedOneOf
import zio.metrics.MetricLabel

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

  val fromMetricKeyAdvice: TypeInstrumentation =
    Instrumentation(named[TypeDescription]("zio.metrics.Metric$")).`with`(
      Advice(
        named[MethodDescription]("fromMetricKey"),
        "io.scalac.mesmer.otelextension.instrumentations.zio.advice.ZIOFromMetricKeyAdvice"
      )
    )

  val taggedAdvice: TypeInstrumentation =
    Instrumentation(named[TypeDescription]("zio.metrics.Metric")).`with`(
      Advice(
        named[MethodDescription]("tagged").and(ElementMatchers.takesArguments(classOf[Set[MetricLabel]])),
        "io.scalac.mesmer.otelextension.instrumentations.zio.advice.ZIOMetricsTaggedAdvice"
      )
    )

  // This advice ensures that every Metric mapping function
  // (a function which creates a new instance of the metric making the old metric subject to GC),
  // carries refference to the newly instantiated metric in the VirtualField.
  val genericMappingAdvice: TypeInstrumentation =
    Instrumentation(named[TypeDescription]("zio.metrics.Metric")).`with`(
      Advice(
        namedOneOf[MethodDescription]("contramap", "map", "mapType", "taggedWith"),
        "io.scalac.mesmer.otelextension.instrumentations.zio.advice.ZIOMetricsGenericMappingAdvice"
      )
    )
}
