package io.scalac.mesmer.otelextension

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import net.bytebuddy.agent.builder.AgentBuilder

import java.lang.instrument.Instrumentation

final case class Agent private (private[agent] val instrumentations: Set[AgentInstrumentation]) extends {

  def ++(other: Agent): Agent = Agent(instrumentations ++ other.instrumentations)

  def ++(other: AgentInstrumentation): Agent = Agent(instrumentations + other)

  def installOnMesmerAgent(builder: AgentBuilder, instrumentation: Instrumentation): LoadingResult =
    instrumentations.toSeq.sorted.map { agentInstrumentation =>
      agentInstrumentation.mesmerInstallation(builder, instrumentation)
    }
      .fold(LoadingResult.empty)(_ ++ _)

  def installOnOtelAgent(): Seq[TypeInstrumentation] = instrumentations.toSeq.sorted.map { agentInstrumentation =>
    agentInstrumentation.otelInstallation
  }
}

object Agent {

  def apply(head: AgentInstrumentation, tail: AgentInstrumentation*): Agent = new Agent(Set.from(head +: tail))

  val empty: Agent = new Agent(Set.empty[AgentInstrumentation])
}
