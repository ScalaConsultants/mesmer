package io.scalac.mesmer.agent.util.i13n

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.matcher.ElementMatcher

import scala.language.implicitConversions

import io.scalac.mesmer.core.typeclasses.Encode
import io.scalac.mesmer.core.typeclasses.Encode._

case class Instrumentation private (
  private[i13n] val instrumentedType: TypeDesc,
  private[i13n] val adviceSet: Set[Advice]
) {
  def withAdvice(advice: Advice): Instrumentation =
    new Instrumentation(instrumentedType, adviceSet + advice)
}

object Instrumentation {
  def apply(forType: TypeDesc): Instrumentation = this(forType, Set())

  implicit val toOtelTypeInstrumentation: Encode[Instrumentation, TypeInstrumentation] = input =>
    new TypeInstrumentation {
      val typeMatcher: ElementMatcher[TypeDescription] = input.instrumentedType

      def transform(transformer: TypeTransformer): Unit =
        input.adviceSet.foreach { it: Advice =>
          transformer.applyAdviceToMethod(it.instrumentedMethod, it.adviceName)
        }
    }

  implicit def convertToOtel(instrumentation: Instrumentation): TypeInstrumentation = instrumentation.encode
}

case class Advice(private[i13n] val instrumentedMethod: MethodDesc, private[i13n] val adviceName: String)
