package io.scalac.mesmer.agent.akka.http

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.support.ModulesSupport
import net.bytebuddy.pool.TypePool

object AkkaHttpAgent extends InstrumentModuleFactory {

  // @ToDo tests all supported versions
  protected final val supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaHttpModule, ModulesSupport.akkaHttp)

  private val httpAgent =
    instrument("akka.http.scaladsl.HttpExt")
      .visit[HttpExtAdvice]("bindAndHandle")

  val agent: Agent = Agent(httpAgent)

}
