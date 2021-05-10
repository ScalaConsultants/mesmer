package io.scalac.mesmer.agent.utils

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation
import org.scalatest.BeforeAndAfterAll
import org.scalatest.TestSuite

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.akka.actor.AkkaActorAgent
import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.agent.akka.stream.AkkaStreamAgent
import io.scalac.mesmer.core.util.ModuleInfo.Modules
import io.scalac.mesmer.core.util.ModuleInfo.extractModulesInformation

object InstallAgent {
  val allInstrumentations: Agent =
    AkkaActorAgent.agent ++ AkkaHttpAgent.agent ++ AkkaPersistenceAgent.agent ++ AkkaStreamAgent.agent
}

abstract class InstallAgent extends TestSuite with BeforeAndAfterAll {

  import InstallAgent._

  def modules: Modules = extractModulesInformation(Thread.currentThread().getContextClassLoader)

  protected def agent: Agent = allInstrumentations

  private val builder = new AgentBuilder.Default()
    .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
    .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
    .`with`(
      AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly()
    )

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val instrumentation = ByteBuddyAgent.install()

    agent
      .installOn(builder, instrumentation, modules)
      .eagerLoad()
  }
}