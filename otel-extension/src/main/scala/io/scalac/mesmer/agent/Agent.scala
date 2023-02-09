package io.scalac.mesmer.agent

import java.security.ProtectionDomain

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.utility.JavaModule

import scala.jdk.CollectionConverters.SeqHasAsJava

final case class Agent private (private[mesmer] val instrumentations: Set[AgentInstrumentation]) extends {

  def ++(other: Agent): Agent = Agent(instrumentations ++ other.instrumentations)

  def ++(other: AgentInstrumentation): Agent = Agent(instrumentations + other)

  def onCondition(condition: Boolean): Agent = if (condition) this else Agent.empty

  def asOtelTypeInstrumentations: java.util.List[TypeInstrumentation] = {

    def toOtelAgentTypeInstrumentation(agentInstrumentation: AgentInstrumentation): TypeInstrumentation = {
      val instrumentation = agentInstrumentation.typeInstrumentation
      new TypeInstrumentation {

        override def typeMatcher(): ElementMatcher[TypeDescription] = instrumentation.`type`.desc

        override def transform(transformer: TypeTransformer): Unit =
          transformer.applyTransformer(new AgentBuilder.Transformer() {
            override def transform(
              builder: DynamicType.Builder[_],
              typeDescription: TypeDescription,
              classLoader: ClassLoader,
              module: JavaModule,
              protectionDomain: ProtectionDomain
            ): DynamicType.Builder[_] = instrumentation.transformBuilder(builder)
          })
      }
    }
    instrumentations.toSeq.sorted.map(toOtelAgentTypeInstrumentation).asJava
  }
}
object Agent {
  def apply(head: AgentInstrumentation, tail: AgentInstrumentation*): Agent = new Agent(Set.from(head +: tail))

  val empty: Agent = new Agent(Set.empty[AgentInstrumentation])
}
