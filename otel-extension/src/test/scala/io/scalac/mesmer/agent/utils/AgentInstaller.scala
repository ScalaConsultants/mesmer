package io.scalac.mesmer.agent.utils

import java.lang.instrument.Instrumentation

import net.bytebuddy.agent.builder.AgentBuilder

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.AgentInstrumentation
import io.scalac.mesmer.agent.util.i13n

trait AgentInstaller {
  def install(agent: Agent): Unit
}

object AgentInstaller {

  def make(agentBuilder: AgentBuilder, instrumentation: Instrumentation): AgentInstaller =
    new AgentInstaller {
      override def install(agent: Agent): Unit = installOnMesmerAgent(agent, agentBuilder, instrumentation)
    }

  private def installOnMesmerAgent(agent: Agent, builder: AgentBuilder, instrumentation: Instrumentation): Unit = {

    def mesmerAgentInstallation(agentInstrumentation: AgentInstrumentation): Unit = {
      val typeInstrumentation: i13n.TypeInstrumentation = agentInstrumentation.typeInstrumentation
      builder
        .`type`(typeInstrumentation.`type`.desc)
        .transform((underlying, _, _, _) => typeInstrumentation.transformBuilder(underlying))
        .installOn(instrumentation)
    }

    // Sorting a set is very brittle when it comes to determining the installation order.
    // See more: https://github.com/ScalaConsultants/mesmer-akka-agent/issues/294
    agent.instrumentations.toSeq.sorted.foreach(mesmerAgentInstallation)
  }
}
