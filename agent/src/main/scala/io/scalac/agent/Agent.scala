package io.scalac.agent

import java.lang.instrument.Instrumentation

import net.bytebuddy.agent.builder.AgentBuilder

abstract class AgentRoot {
  def installOn(instrumentation: Instrumentation): Unit = ()

  def transformEagerly(): Unit = ()
}

trait Agent extends AgentRoot {

  def agentBuilder: AgentBuilder
}
