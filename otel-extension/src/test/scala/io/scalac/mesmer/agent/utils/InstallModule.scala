package io.scalac.mesmer.agent.utils

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation
import org.scalatest.BeforeAndAfterAll
import org.scalatest.TestSuite

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n.InstrumentModuleFactory
import io.scalac.mesmer.core.module.MesmerModule

abstract class InstallModule[M <: MesmerModule](moduleFactory: InstrumentModuleFactory[M])
    extends TestSuite
    with BeforeAndAfterAll {

  protected def agent: Agent = moduleFactory.agent

  private val builder = new AgentBuilder.Default(
    new ByteBuddy()
      .`with`(TypeValidation.DISABLED)
  )
    .`with`(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
    .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
    .`with`(
      AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly()
    )

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val instrumentation = ByteBuddyAgent.install()

    AgentInstaller.make(builder, instrumentation).install(agent)
  }

}
