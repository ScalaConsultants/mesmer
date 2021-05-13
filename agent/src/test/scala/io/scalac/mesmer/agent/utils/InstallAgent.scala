package io.scalac.mesmer.agent.utils

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.akka.actor.{ AkkaActorAgent, AkkaMailboxAgent }
import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.agent.akka.stream.AkkaStreamAgent
import io.scalac.mesmer.core.util.ModuleInfo.{ extractModulesInformation, Modules }
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.{ MethodGraph, TypeValidation }
import net.bytebuddy.pool.TypePool
import org.scalatest.{ BeforeAndAfterAll, TestSuite }

object InstallAgent {
  def allInstrumentations(typePool: TypePool): Agent =
    AkkaActorAgent.agent ++ AkkaHttpAgent.agent ++ AkkaPersistenceAgent.agent ++ AkkaStreamAgent.agent ++ AkkaMailboxAgent
      .agent(typePool)
}

abstract class InstallAgent extends TestSuite with BeforeAndAfterAll {

  import InstallAgent._


  def modules: Modules = extractModulesInformation(Thread.currentThread().getContextClassLoader)

  def pool: TypePool = TypePool.Default.ofSystemLoader()

  protected def agent: Agent = allInstrumentations(pool)

  private val builder = new AgentBuilder.Default(
    new ByteBuddy()
      .`with`(TypeValidation.DISABLED)
//      .`with`(MethodGraph.Compiler.Default.forJVMHierarchy())
  )
    .`with`(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
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
