package io.scalac.agent.utils

import io.scalac.agent.Agent
import io.scalac.agent.akka.actor.AkkaActorAgent
import io.scalac.agent.akka.http.AkkaHttpAgent
import io.scalac.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.agent.akka.stream.AkkaStreamAgent
import io.scalac.core.util.ModuleInfo.{ extractModulesInformation, Modules }
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation
import org.scalatest.{ BeforeAndAfterAll, Suite }

object InstallAgent {
  val allInstrumentations =
    AkkaActorAgent.agent ++ AkkaHttpAgent.agent ++ AkkaPersistenceAgent.agent ++ AkkaStreamAgent.agent
}

trait InstallAgent extends BeforeAndAfterAll {
  this: Suite =>
  import InstallAgent._

  def modules: Modules = extractModulesInformation(Thread.currentThread().getContextClassLoader)

  protected def agent: Agent = allInstrumentations

  private val builder = new AgentBuilder.Default()
    .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
    .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
    .`with`(
      AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly
    )
    .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val instrumentation = ByteBuddyAgent.install()

    agent
      .installOn(builder, instrumentation, modules)
      .eagerLoad()
  }
}
