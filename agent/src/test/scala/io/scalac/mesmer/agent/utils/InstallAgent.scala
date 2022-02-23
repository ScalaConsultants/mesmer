package io.scalac.mesmer.agent.utils

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.akka.actor.AkkaActorAgent
import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.agent.akka.stream.AkkaStreamAgent
import io.scalac.mesmer.agent.util.i13n.InstrumentModuleFactory
import io.scalac.mesmer.agent.utils.InstallAgent.allInstrumentations
import io.scalac.mesmer.core.module.MesmerModule
import io.scalac.mesmer.core.util.LibraryInfo.{ extractModulesInformation, LibraryInfo }
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation
import org.scalatest.{ BeforeAndAfterAll, TestSuite }

object InstallAgent {

  def allInstrumentations(info: LibraryInfo): Agent = AkkaActorAgent.agent(info) ++
    AkkaHttpAgent.agent(info) ++
    AkkaPersistenceAgent.agent(info) ++
    AkkaStreamAgent.agent(info)
}

abstract class InstallAgent extends TestSuite with BeforeAndAfterAll {

  def jars: LibraryInfo = extractModulesInformation(Thread.currentThread().getContextClassLoader)

  protected def agent: Agent = allInstrumentations(jars)

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

    agent
      .installOnMesmerAgent(builder, instrumentation)
      .eagerLoad()
  }
}

abstract class InstallModule[M <: MesmerModule](
  moduleFactory: InstrumentModuleFactory[M]
) extends InstallAgent {

  override protected def agent: Agent = moduleFactory.agent(jars)

}
