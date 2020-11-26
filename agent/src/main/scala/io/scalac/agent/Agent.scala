package io.scalac.agent

import java.lang.instrument.Instrumentation

import net.bytebuddy.agent.builder.AgentBuilder

final case class Agent(installOn: (AgentBuilder, Instrumentation) => Unit, transformEagerly: () => Unit) { self =>

  def ++(that: Agent): Agent =
    Agent(
      (builder, instrumentation) => {
        self.installOn(builder, instrumentation)
        that.installOn(builder, instrumentation)
      },
      () => {
        self.transformEagerly()
        that.transformEagerly()
      }
    )
}