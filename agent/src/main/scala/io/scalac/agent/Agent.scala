package io.scalac.agent

import java.lang.instrument.Instrumentation

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.model.SupportedModules
import io.scalac.agent.util.ModuleInfo.Modules
import net.bytebuddy.agent.builder.AgentBuilder
import org.slf4j.LoggerFactory

object Agent {

  private val logger = LoggerFactory.getLogger(classOf[Agent])

  def apply(head: AgentInstrumentation, tail: AgentInstrumentation*): Agent = Agent((head +: tail).toSet)

  class LoadingResult(val fqns: Seq[String]) {
    import LoadingResult.{ logger => loadingLogger }
    def eagerLoad(): Unit =
      fqns.foreach { className =>
        try {
          Thread.currentThread().getContextClassLoader.loadClass(className)
        } catch {
          case _: ClassNotFoundException => loadingLogger.error(s"Couldn't load class ${className}")
        }
      }

    def ++(other: LoadingResult): LoadingResult = new LoadingResult(this.fqns ++ other.fqns)
  }

  object LoadingResult {
    private val logger = LoggerFactory.getLogger(classOf[LoadingResult])

    def apply(fqns: Seq[String]): LoadingResult = new LoadingResult(fqns)

    def apply(fqn: String, fqns: String*): LoadingResult = apply(fqn +: fqns)

    def empty: LoadingResult = new LoadingResult(Seq.empty)
  }
}

object AgentInstrumentation {

  def apply(name: String, modules: SupportedModules)(
    installation: (AgentBuilder, Instrumentation, Modules) => LoadingResult
  ): AgentInstrumentation =
    new AgentInstrumentation(name, modules) {
      override def apply(builder: AgentBuilder, instrumentation: Instrumentation, modules: Modules): LoadingResult =
        installation(builder, instrumentation, modules)
    }
}

sealed abstract case class AgentInstrumentation(name: String, instrumentingModules: SupportedModules)
    extends ((AgentBuilder, Instrumentation, Modules) => LoadingResult) {

  override def hashCode(): Int           = name.hashCode()
  override def equals(obj: Any): Boolean = name.equals(obj) // instrumentations should be equal when name is the same
}

final case class Agent private (private val set: Set[AgentInstrumentation]) extends {
  import Agent._

  def ++(other: Agent): Agent = Agent(set ++ other.set)

  def ++(other: AgentInstrumentation): Agent = Agent(set + other)

  def installOn(builder: AgentBuilder, instrumentation: Instrumentation, modules: Modules): LoadingResult =
    set.flatMap { agentInstrumentation =>
      val dependencies = agentInstrumentation.instrumentingModules

      val allModulesSupported = dependencies.modules.forall { module =>
        (for {
          detectedVersion <- modules.get(module)
        } yield dependencies.supportedVersion(module).supports(detectedVersion)).getOrElse(false)
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
