package io.scalac.mesmer.agent

import io.scalac.mesmer.agent.Agent.LoadingResult
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.util.ModuleInfo.Modules
import net.bytebuddy.agent.builder.AgentBuilder
import org.slf4j.LoggerFactory

import java.lang.instrument.Instrumentation

object Agent {

  private val logger = LoggerFactory.getLogger(classOf[Agent])

  def apply(head: AgentInstrumentation, tail: AgentInstrumentation*): Agent = new Agent((head +: tail).toSet)

  val empty: Agent = new Agent(Set.empty)

  class LoadingResult(val fqns: Set[String]) {
    import LoadingResult.{ logger => loadingLogger }
    def eagerLoad(): Unit =
      fqns.foreach { className =>
        try Thread.currentThread().getContextClassLoader.loadClass(className)
        catch {
          case _: ClassNotFoundException => loadingLogger.error("Couldn't load class {}", className)
        }
      }

    def ++(other: LoadingResult): LoadingResult = new LoadingResult(this.fqns ++ other.fqns)

    override def hashCode(): Int = fqns.hashCode()

    override def equals(obj: Any): Boolean = obj match {
      case loadingResult: LoadingResult => loadingResult.fqns == this.fqns
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

object AgentInstrumentation {

  def apply(name: String, modules: SupportedModules, tags: Set[String])(
    installation: (AgentBuilder, Instrumentation, Modules) => LoadingResult
  ): AgentInstrumentation =
    new AgentInstrumentation(name, modules, tags) {
      def apply(builder: AgentBuilder, instrumentation: Instrumentation, modules: Modules): LoadingResult =
        installation(builder, instrumentation, modules)
    }

}

//TODO add tests
sealed abstract case class AgentInstrumentation(
  name: String,
  instrumentingModules: SupportedModules,
  tags: Set[String]
) extends ((AgentBuilder, Instrumentation, Modules) => LoadingResult)
    with Equals {

  override def hashCode(): Int = name.hashCode()

  override def canEqual(that: Any): Boolean = that.isInstanceOf[AgentInstrumentation]

  override def equals(obj: Any): Boolean = obj match {
    case that: AgentInstrumentation if that.canEqual(this) =>
      tags == that.tags && name == that.name
    case _ => false
  }
}

final case class Agent private (private[agent] val instrumentations: Set[AgentInstrumentation]) extends {
  import Agent._

  def ++(other: Agent): Agent = Agent(instrumentations ++ other.instrumentations)

  def ++(other: AgentInstrumentation): Agent = Agent(instrumentations + other)

  def installOn(builder: AgentBuilder, instrumentation: Instrumentation, modules: Modules): LoadingResult =
    instrumentations.flatMap { agentInstrumentation =>
      val dependencies = agentInstrumentation.instrumentingModules

      val allModulesSupported = dependencies.modules.forall { module =>
        modules
          .get(module)
          .exists(dependencies.supportedVersion(module).supports)
      }

      if (allModulesSupported) {
        val requiredModules = modules.view.filterKeys(dependencies.modules.contains)
        Some(agentInstrumentation(builder, instrumentation, requiredModules.toMap))
      } else {
        logger.error("Unsupported versions for instrumentation for {}", agentInstrumentation.name)
        None
      }
    }.fold(LoadingResult.empty)(_ ++ _)
}
