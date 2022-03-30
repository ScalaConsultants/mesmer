package io.scalac.mesmer.agent.akka.http

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.module.AkkaHttpModule

object AkkaHttpAgent
    extends InstrumentModuleFactory(AkkaHttpModule)
    with AkkaHttpModule.AkkaHttpConnectionsMetricsDef[Agent]
    with AkkaHttpModule.AkkaHttpRequestMetricsDef[Agent] {

  val requestTime: Agent = requestEvents

  val requestCounter: Agent = requestEvents

  val connections: Agent = connectionEvents

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
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Some if feature can be enabled, None otherwise
   */
  def agent: Agent = {
    val config = module.enabled

    List(
      requestCounter.onCondition(config.requestCounter),
      requestTime.onCondition(config.requestTime),
      connections.onCondition(config.connections)
    ).reduce(_ ++ _)
  }

}
