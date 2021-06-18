package io.scalac.mesmer.agent.akka.http

import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.agent.{ Agent, AgentInstrumentation }
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.core.support.ModulesSupport

object AkkaHttpAgent
    extends InstrumentModuleFactoryTest(AkkaHttpModule)
    with AkkaHttpModule.Metrics[AgentInstrumentation] {

  // @ToDo tests all supported versions
  protected val supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaHttpModule, ModulesSupport.akkaHttp)

  def requestTime: AgentInstrumentation = httpAgent

  def requestCounter: AgentInstrumentation = httpAgent

  private val httpAgent: AgentInstrumentation =
    instrument("akka.http.scaladsl.HttpExt")
      .visit[HttpExtAdvice]("bindAndHandle")

  def agent(config: AkkaHttpModule.All[Boolean]): Agent = {

    val requestCounterAgent = if (config.requestCounter) Agent(requestCounter) else Agent.empty
    val requestTimeAgent    = if (config.requestTime) Agent(requestTime) else Agent.empty

    requestCounterAgent ++ requestTimeAgent
  }
}
