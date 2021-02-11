package io.scalac.agent

import java.lang.instrument.Instrumentation

import io.scalac.agent.akka.http.AkkaHttpAgent
import io.scalac.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.core.util.ModuleInfo
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

    val allInstrumentations = AkkaPersistenceAgent.agent ++ AkkaHttpAgent.agent
    val moduleInfo          = ModuleInfo.extractModulesInformation(Thread.currentThread().getContextClassLoader)

    allInstrumentations
      .installOn(agentBuilder, instrumentation, moduleInfo)
      .eagerLoad()
  }
}
