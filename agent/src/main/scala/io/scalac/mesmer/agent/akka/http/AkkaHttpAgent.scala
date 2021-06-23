package io.scalac.mesmer.agent.akka.http

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.core.support.ModulesSupport

object AkkaHttpAgent
    extends InstrumentModuleFactory(AkkaHttpModule)
    with AkkaHttpModule.AkkaHttpConnectionsMetricsDef[Agent]
    with AkkaHttpModule.AkkaHttpRequestMetricsDef[Agent] {

  // @ToDo tests all supported versions
  protected val supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaHttpModule, ModulesSupport.akkaHttp)

  def requestTime: Agent = requestEvents

  def requestCounter: Agent = requestEvents

  def connections: Agent = connectionEvents

  private lazy val requestEvents =
    Agent(
      instrument("akka.http.scaladsl.HttpExt".fqcnWithTags("requests"))
        .visit[HttpExtRequestsAdvice]("bindAndHandle")
    )

  private lazy val connectionEvents =
    Agent(
      instrument("akka.http.scaladsl.HttpExt".fqcnWithTags("connections"))
        .visit[HttpExtConnectionsAdvice]("bindAndHandle")
    )

  def agent(config: AkkaHttpModule.All[Boolean]): Agent = {

    val requestCounterAgent = if (config.requestCounter) requestCounter else Agent.empty
    val requestTimeAgent    = if (config.requestTime) requestTime else Agent.empty
    val connectionsAgent    = if (config.connections) connections else Agent.empty

    requestCounterAgent ++ requestTimeAgent ++ connectionsAgent
  }
}
