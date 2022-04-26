package io.scalac.mesmer.agent.utils

import java.lang.instrument.Instrumentation

import net.bytebuddy.agent.builder.AgentBuilder
import org.slf4j.LoggerFactory

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.AgentInstrumentation
import io.scalac.mesmer.agent.util.i13n
import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails
import io.scalac.mesmer.agent.utils.AgentInstaller.LoadingResult

trait AgentInstaller {
  def install(agent: Agent): LoadingResult
}

object AgentInstaller {

  def make(agentBuilder: AgentBuilder, instrumentation: Instrumentation): AgentInstaller =
    new AgentInstaller {
      override def install(agent: Agent): LoadingResult = installOnMesmerAgent(agent, agentBuilder, instrumentation)
    }

  private def installOnMesmerAgent(
    agent: Agent,
    builder: AgentBuilder,
    instrumentation: Instrumentation
  ): LoadingResult = {

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
    agent.instrumentations.toSeq.sorted
      .map(mesmerAgentInstallation)
      .fold(LoadingResult.empty)(_ ++ _)
  }

  object LoadingResult {
    private val logger = LoggerFactory.getLogger(classOf[LoadingResult])

    def apply(fqns: Seq[String]): LoadingResult = new LoadingResult(fqns.toSet)

    def apply(fqn: String, fqns: String*): LoadingResult = apply(fqn +: fqns)

    def empty: LoadingResult = new LoadingResult(Set.empty)
  }

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
}
