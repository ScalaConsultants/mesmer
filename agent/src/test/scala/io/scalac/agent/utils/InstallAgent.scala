package io.scalac.agent.utils

import io.scalac.agent.Agent
import io.scalac.core.util.ModuleInfo.{ extractModulesInformation, Modules }
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation
import org.scalatest.{ BeforeAndAfterAll, Suite }

trait InstallAgent extends BeforeAndAfterAll {
  this: Suite =>

  def modules: Modules = extractModulesInformation(Thread.currentThread().getContextClassLoader)

  def agent: Agent

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val instrumentation = ByteBuddyAgent.install()

    val builder = new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
      .`with`(
        AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly
      )
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)

    agent
      .installOn(builder, instrumentation, modules)
      .eagerLoad()
  }

}