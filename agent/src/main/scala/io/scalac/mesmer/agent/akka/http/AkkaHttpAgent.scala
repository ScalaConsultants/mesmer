package io.scalac.mesmer.agent.akka.http

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.core.module.AkkaHttpModule._

object AkkaHttpAgent
    extends InstrumentModuleFactory(AkkaHttpModule)
    with AkkaHttpModule.AkkaHttpConnectionsMetricsDef[AkkaHttpModule.AkkaJar[Version] => Option[Agent]]
    with AkkaHttpModule.AkkaHttpRequestMetricsDef[AkkaHttpModule.AkkaJar[Version] => Option[Agent]] {

  def requestTime: AkkaHttpModule.Jars[Version] => Option[Agent] = _ => Some(requestEvents) // Version => Option[Agent]

  def requestCounter: AkkaHttpModule.Jars[Version] => Option[Agent] = _ => Some(requestEvents)

  def connections: AkkaHttpModule.Jars[Version] => Option[Agent] = _ => Some(connectionEvents)

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

  /**
   * @param config configuration of features that are wanted by the user
   * @param jars   versions of required jars to deduce which features can be enabled
   * @return Some if feature can be enabled, None otherwise
   */
  override def agent(
    config: AkkaHttpModule.All[Boolean],
    jars: AkkaHttpModule.Jars[Version]
  ): (Agent, AkkaHttpModule.All[Boolean]) = {
    val requestCounterAgent = if (config.requestCounter) requestCounter(jars) else None
    val requestTimeAgent    = if (config.requestTime) requestTime(jars) else None
    val connectionsAgent    = if (config.connections) connections(jars) else None

    val resultantAgent = requestCounterAgent.getOrElse(Agent.empty) ++ requestTimeAgent.getOrElse(
      Agent.empty
    ) ++ connectionsAgent.getOrElse(Agent.empty)

    val enabled = Impl[Boolean](requestCounterAgent.isDefined, requestTimeAgent.isDefined, connectionsAgent.isDefined)

    (resultantAgent, enabled)
  }

}
