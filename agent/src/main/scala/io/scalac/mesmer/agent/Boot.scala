package io.scalac.mesmer.agent

import java.lang.instrument.Instrumentation

import io.opentelemetry.instrumentation.api.config.Config
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation

import scala.annotation.unused

import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent

object Boot {

  Config.internalInitializeConfig(
    Config
      .builder()
      .readSystemProperties()
      .readEnvironmentVariables()
      .build()
  )

  def premain(@unused arg: String, instrumentation: Instrumentation): Unit = {

    val agentBuilder = new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
      .`with`(
        AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly
      )
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)

    val allInstrumentations = AkkaPersistenceAgent.agent

    allInstrumentations
      .installOnMesmerAgent(agentBuilder, instrumentation)
      .eagerLoad()

  }
}
