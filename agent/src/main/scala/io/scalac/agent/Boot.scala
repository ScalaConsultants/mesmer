package io.scalac.agent

import java.lang.instrument.Instrumentation

import io.scalac.agent.akka.http.RouteAgent
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation

object Boot {

  def premain(args: String, instrumentation: Instrumentation): Unit = {

    object AllInstrumentations extends AgentRoot with AkkaPersistenceAgent with RouteAgent {
      override lazy val agentBuilder: AgentBuilder =
        new AgentBuilder.Default()
          .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
          .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
          .`with`(
            AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly
          )
          .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)
    }

    AllInstrumentations.installOn(instrumentation)
    AllInstrumentations.transformEagerly()
  }
}
