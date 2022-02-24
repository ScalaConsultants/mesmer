package io.scalac.mesmer.agent

import java.lang.instrument.Instrumentation

import io.opentelemetry.javaagent.tooling.config.ConfigInitializer
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation

import scala.annotation.unused

import io.scalac.mesmer.agent.akka.actor.AkkaActorAgent
import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.agent.akka.stream.AkkaStreamAgent

object Boot {

  def premain(@unused arg: String, instrumentation: Instrumentation): Unit = {

    // must be called only once
    ConfigInitializer.initialize()

    val agentBuilder = new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
      .`with`(
        AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly
      )
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)

    val allInstrumentations = AkkaPersistenceAgent.agent ++
      AkkaStreamAgent.agent ++
      AkkaHttpAgent.agent ++
      AkkaActorAgent.agent

    allInstrumentations
      .installOnMesmerAgent(agentBuilder, instrumentation)
      .eagerLoad()

  }
}
