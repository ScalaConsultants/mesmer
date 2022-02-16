package io.scalac.mesmer.agent

import java.lang.instrument.Instrumentation

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatcher
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.SeqHasAsJava

import io.scalac.mesmer.agent.util.i13n
import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails

final case class Agent private (private[agent] val instrumentations: Set[AgentInstrumentation]) extends {
  import Agent._

  def ++(other: Agent): Agent = Agent(instrumentations ++ other.instrumentations)

  def emptyOnCondition(condition: Boolean): Agent = if (condition) this else Agent.empty

  def ++(other: AgentInstrumentation): Agent = Agent(instrumentations + other)

  def installOnMesmerAgent(builder: AgentBuilder, instrumentation: Instrumentation): LoadingResult = {

    def mesmerAgentInstallation(agentInstrumentation: AgentInstrumentation): LoadingResult = {
      val typeInstrumentation: i13n.TypeInstrumentation     = agentInstrumentation.typeInstrumentation
      val instrumentationDetails: InstrumentationDetails[_] = typeInstrumentation.`type`.name
      builder
        .`type`(typeInstrumentation.`type`.desc)
        .transform((underlying, _, _, _) => typeInstrumentation.transformBuilder(underlying))
        .installOn(instrumentation)
      if (instrumentationDetails.isFQCN) LoadingResult(instrumentationDetails.name +: agentInstrumentation.load)
      else LoadingResult.empty
    }

    // Sorting a set is very brittle when it comes to determining the installation order.
    // See more: https://github.com/ScalaConsultants/mesmer-akka-agent/issues/294
    instrumentations.toSeq.sorted
      .map(mesmerAgentInstallation)
      .fold(LoadingResult.empty)(_ ++ _)
  }

  def asOtelTypeInstrumentations: java.util.List[TypeInstrumentation] = {

    def toOtelAgentTypeInstrumentation(agentInstrumentation: AgentInstrumentation): TypeInstrumentation = {
      val instrumentation = agentInstrumentation.typeInstrumentation
      new TypeInstrumentation {

        override def typeMatcher(): ElementMatcher[TypeDescription] = instrumentation.`type`.desc

        override def transform(transformer: TypeTransformer): Unit =
          transformer.applyTransformer { (builder: DynamicType.Builder[_], _, _, _) =>
            instrumentation.transformBuilder(builder)
          }
      }
    }

    instrumentations.toSeq.sorted.map(toOtelAgentTypeInstrumentation).asJava

  }
}
object Agent {

  def apply(head: AgentInstrumentation, tail: AgentInstrumentation*): Agent = new Agent(Set.from(head +: tail))

  val empty: Agent = new Agent(Set.empty[AgentInstrumentation])

  final case class LoadingResult(fullyQualifiedNames: Set[String]) {
    import LoadingResult.{ logger => loadingLogger }
    def eagerLoad(): Unit =
      fullyQualifiedNames.foreach { className =>
        try Thread.currentThread().getContextClassLoader.loadClass(className)
        catch {
          case _: ClassNotFoundException => loadingLogger.error("Couldn't load class {}", className)
        }
      }

    def ++(other: LoadingResult): LoadingResult = new LoadingResult(
      this.fullyQualifiedNames ++ other.fullyQualifiedNames
    )

    override def hashCode(): Int = fullyQualifiedNames.hashCode()

    override def equals(obj: Any): Boolean = obj match {
      case loadingResult: LoadingResult => loadingResult.fullyQualifiedNames == this.fullyQualifiedNames
      case _                            => false
    }
  }

  object LoadingResult {
    private val logger = LoggerFactory.getLogger(classOf[LoadingResult])

    def apply(fqns: Seq[String]): LoadingResult = new LoadingResult(fqns.toSet)

    def apply(fqn: String, fqns: String*): LoadingResult = apply(fqn +: fqns)

    def empty: LoadingResult = new LoadingResult(Set.empty)
  }
}
