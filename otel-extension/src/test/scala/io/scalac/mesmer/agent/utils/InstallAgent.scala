package io.scalac.mesmer.agent.utils

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy
import net.bytebuddy.dynamic.scaffold.TypeValidation
import org.scalatest.BeforeAndAfterAll
import org.scalatest.TestSuite

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n.InstrumentModuleFactory
import io.scalac.mesmer.agent.utils.InstallAgent.allInstrumentations
import io.scalac.mesmer.core.module.MesmerModule
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamAgent

object InstallAgent {

  def allInstrumentations: Agent = AkkaStreamAgent.agent
}

abstract class InstallAgent extends TestSuite with BeforeAndAfterAll {

  protected def agent: Agent = allInstrumentations

  private val builder = new AgentBuilder.Default(
    new ByteBuddy()
      .`with`(TypeValidation.DISABLED)
  )
    .`with`(AgentBuilder.RedefinitionStrategy.REDEFINITION)
    .`with`(TypeStrategy.Default.REDEFINE)
    .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
    .`with`(
      AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly()
    )

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val instrumentation = ByteBuddyAgent.install()

    agent
      .installOnMesmerAgent(builder, instrumentation)
      .eagerLoad()
  }
}

abstract class InstallModule[M <: MesmerModule](
  moduleFactory: InstrumentModuleFactory[M]
) extends InstallAgent {

  override protected def agent: Agent = moduleFactory.agent

}
