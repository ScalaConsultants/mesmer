package io.scalac.mesmer.agent

import java.lang.instrument.Instrumentation

import net.bytebuddy.agent.builder.AgentBuilder
import org.slf4j.LoggerFactory

import io.scalac.mesmer.agent.Agent.LoadingResult

object Agent {

  def apply(head: AgentInstrumentation, tail: AgentInstrumentation*): Agent = new Agent(Set.from(head +: tail))

  val empty: Agent = new Agent(Set.empty[AgentInstrumentation])

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

  def apply(name: String, tags: Set[String], deferred: Boolean)(
    installation: (AgentBuilder, Instrumentation) => LoadingResult
  ): AgentInstrumentation =
    new AgentInstrumentation(name, tags, deferred) {
      def apply(builder: AgentBuilder, instrumentation: Instrumentation): LoadingResult =
        installation(builder, instrumentation)
    }

}

sealed abstract case class AgentInstrumentation(
  name: String,
  tags: Set[String],
  private val deferred: Boolean
) extends ((AgentBuilder, Instrumentation) => LoadingResult)
    with Equals
    with Ordered[AgentInstrumentation] {

  override def hashCode(): Int = name.hashCode()

  override def canEqual(that: Any): Boolean = that.isInstanceOf[AgentInstrumentation]

  override def equals(obj: Any): Boolean = obj match {
    case that: AgentInstrumentation if that.canEqual(this) =>
      tags == that.tags && name == that.name
    case _ => false
  }

  final def compare(that: AgentInstrumentation): Int = Ordering[Boolean].compare(this.deferred, that.deferred)
}

final case class Agent private (private[agent] val instrumentations: Set[AgentInstrumentation]) extends {
  import Agent._

  def ++(other: Agent): Agent = Agent(instrumentations ++ other.instrumentations)

  def ++(other: AgentInstrumentation): Agent = Agent(instrumentations + other)

  def installOn(builder: AgentBuilder, instrumentation: Instrumentation): LoadingResult =
    instrumentations.toSeq.sorted.map { agentInstrumentation =>
      agentInstrumentation(builder, instrumentation)

    }.fold(LoadingResult.empty)(_ ++ _)
}
