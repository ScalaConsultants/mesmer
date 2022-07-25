package io.scalac.mesmer.agent.util.i13n

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import io.opentelemetry.javaagent.extension.instrumentation.{ TypeInstrumentation => OtelTypeInstrumentation }
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.matcher.ElementMatcher

import scala.language.implicitConversions

import io.scalac.mesmer.core.typeclasses.Encode
import io.scalac.mesmer.core.typeclasses.Encode._

case class Instrumentation private (
  private[i13n] val instrumentedType: TypeDesc,
  private[i13n] val adviceSet: Set[Advice]
) {
  def `with`(advice: Advice): Instrumentation =
    new Instrumentation(instrumentedType, adviceSet + advice)
}

object Instrumentation {
  def apply(forType: TypeDesc): Instrumentation = this(forType, Set())

  implicit val toOtelTypeInstrumentation: Encode[Instrumentation, OtelTypeInstrumentation] = input =>
    new OtelTypeInstrumentation {
      val typeMatcher: ElementMatcher[TypeDescription] = input.instrumentedType

      def transform(transformer: TypeTransformer): Unit =
        input.adviceSet.foreach {
          case ForMethod(instrumentedMethod, adviceName) =>
            transformer.applyAdviceToMethod(instrumentedMethod, adviceName)
          case ForTransformer(agentTransformer) =>
            transformer.applyTransformer(agentTransformer)
        }
    }

  implicit def convertToOtel(instrumentation: Instrumentation): OtelTypeInstrumentation = instrumentation.encode
}

sealed trait Advice

object Advice {
  def apply(instrumentedMethod: MethodDesc, adviceName: String): Advice = ForMethod(instrumentedMethod, adviceName)
  def apply(transformer: AgentBuilder.Transformer): Advice              = ForTransformer(transformer)
}

private case class ForMethod(instrumentedMethod: MethodDesc, adviceName: String) extends Advice
private case class ForTransformer(transformer: AgentBuilder.Transformer)         extends Advice
