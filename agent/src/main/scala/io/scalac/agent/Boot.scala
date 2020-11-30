package io.scalac.agent

import java.lang.instrument.Instrumentation

import io.scalac.agent.akka.cluster.AkkaClusterAgent
import io.scalac.agent.akka.http.AkkaHttpAgent
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation

object Boot {

  def premain(args: String, instrumentation: Instrumentation): Unit = {

    val agentBuilder = new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
      .`with`(
        AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly
      )
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)

    val allInstrumentations = AkkaPersistenceAgent.agent ++ AkkaHttpAgent.agent ++ AkkaClusterAgent.agent

    allInstrumentations.installOn(agentBuilder, instrumentation)
    allInstrumentations.transformEagerly()
  }
}
