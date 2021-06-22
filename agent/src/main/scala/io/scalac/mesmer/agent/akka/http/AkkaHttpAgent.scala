package io.scalac.mesmer.agent.akka.http

import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.agent.{ Agent, AgentInstrumentation }
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.core.support.ModulesSupport

object AkkaHttpAgent
    extends InstrumentModuleFactoryTest(AkkaHttpModule)
    with AkkaHttpModule.AkkaHttpConnectionsMetricsDef[AgentInstrumentation]
    with AkkaHttpModule.AkkaHttpRequestMetricsDef[AgentInstrumentation] {

  // @ToDo tests all supported versions
  protected val supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaHttpModule, ModulesSupport.akkaHttp)

  def requestTime: AgentInstrumentation = requestEvents

  def requestCounter: AgentInstrumentation = requestEvents

  def connections: AgentInstrumentation = connectionEvents

  private lazy val requestEvents =
    instrument(`type`("akka.http.scaladsl.HttpExt?requests", "akka.http.scaladsl.HttpExt"))
      .visit[HttpExtRequestsAdvice]("bindAndHandle")

  private lazy val connectionEvents =
    instrument(`type`("akka.http.scaladsl.HttpExt?connections", "akka.http.scaladsl.HttpExt"))
      .visit[HttpExtConnectionsAdvice]("bindAndHandle")

  def agent(config: AkkaHttpModule.All[Boolean]): Agent = {

    val requestCounterAgent = if (config.requestCounter) Agent(requestCounter) else Agent.empty
    val requestTimeAgent    = if (config.requestTime) Agent(requestTime) else Agent.empty
    val connectionsAgent    = if (config.connections) Agent(connections) else Agent.empty

    requestCounterAgent ++ requestTimeAgent ++ connectionsAgent
  }
}
