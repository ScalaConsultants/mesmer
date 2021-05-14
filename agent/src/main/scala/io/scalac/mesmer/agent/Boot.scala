package io.scalac.mesmer.agent

import java.lang.instrument.Instrumentation

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation

import scala.annotation.unused

import io.scalac.mesmer.agent.akka.actor.AkkaActorAgent
import io.scalac.mesmer.agent.akka.actor.AkkaMailboxAgent
import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.agent.akka.stream.AkkaStreamAgent
import io.scalac.mesmer.core.util.ModuleInfo

object Boot {

  def premain(@unused arg: String, instrumentation: Instrumentation): Unit = {

    val agentBuilder = new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
      .`with`(
        AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly
      )
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)

    val allInstrumentations =
      AkkaPersistenceAgent.agent ++ AkkaHttpAgent.agent ++ AkkaStreamAgent.agent ++ AkkaActorAgent.agent ++ AkkaMailboxAgent.agent
    val moduleInfo = ModuleInfo.extractModulesInformation(Thread.currentThread().getContextClassLoader)

    allInstrumentations
      .installOn(agentBuilder, instrumentation, moduleInfo)
      .eagerLoad()

  }
}
