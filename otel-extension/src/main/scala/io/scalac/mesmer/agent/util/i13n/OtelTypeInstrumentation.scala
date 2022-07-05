package io.scalac.mesmer.agent.util.i13n

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.matcher.ElementMatcher

import io.scalac.mesmer.core.typeclasses.Encode

case class OtelTypeInstrumentation private (
  private[i13n] val instrumentedType: TypeDesc,
  private[i13n] val adviceSet: Set[AdviceApplication]
) {
  def using(advice: AdviceApplication): OtelTypeInstrumentation =
    new OtelTypeInstrumentation(instrumentedType, adviceSet + advice)
}

object OtelTypeInstrumentation {
  def instrument(instrumentedType: TypeDesc): OtelTypeInstrumentation = this(instrumentedType, Set())

  implicit val toOtelTypeInstrumentation: Encode[OtelTypeInstrumentation, TypeInstrumentation] =
    new Encode[OtelTypeInstrumentation, TypeInstrumentation] {
      override def encode(input: OtelTypeInstrumentation): TypeInstrumentation = new TypeInstrumentation {
        override def typeMatcher(): ElementMatcher[TypeDescription] = input.instrumentedType

        override def transform(transformer: TypeTransformer): Unit =
          input.adviceSet.foreach { it: AdviceApplication =>
            transformer.applyAdviceToMethod(it.instrumentedMethod, it.adviceName)
          }
      }
    }

  implicit class OtelTypeInstrumentationEncodeOps(i: OtelTypeInstrumentation) {
    def encode(): TypeInstrumentation = toOtelTypeInstrumentation.encode(i)
  }

  implicit def convertToOtel(instrumentation: OtelTypeInstrumentation): TypeInstrumentation =
    instrumentation.encode()
}

case class AdviceApplication private (
  private[i13n] val instrumentedMethod: MethodDesc,
  private[i13n] val adviceName: String
)

object AdviceApplication {
  def advice(instrumentedMethod: MethodDesc, adviceName: String): AdviceApplication =
    this(instrumentedMethod, adviceName)
}
