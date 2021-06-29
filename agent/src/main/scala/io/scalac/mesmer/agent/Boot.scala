package io.scalac.mesmer.agent

import com.typesafe.config.ConfigFactory
import io.scalac.mesmer.agent.akka.actor.AkkaActorAgent
import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.agent.akka.stream.AkkaStreamAgent
import io.scalac.mesmer.core.util.LibraryInfo
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation

import java.lang.instrument.Instrumentation
import scala.annotation.unused

object Boot {

  //TODO better configuration specification
  def premain(@unused arg: String, instrumentation: Instrumentation): Unit = {

    val config = ConfigFactory.load()

    val agentBuilder = new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
      .`with`(
        AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly
      )
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)

    val info = LibraryInfo.extractModulesInformation(Thread.currentThread().getContextClassLoader)

    val allInstrumentations = AkkaPersistenceAgent.initAgent(info, config).getOrElse(Agent.empty) ++
      AkkaStreamAgent.initAgent(info, config).getOrElse(Agent.empty) ++
      AkkaHttpAgent.initAgent(info, config).getOrElse(Agent.empty) ++
      AkkaActorAgent.initAgent(info, config).getOrElse(Agent.empty)

    allInstrumentations
      .installOn(agentBuilder, instrumentation)
      .eagerLoad()

  }
}
