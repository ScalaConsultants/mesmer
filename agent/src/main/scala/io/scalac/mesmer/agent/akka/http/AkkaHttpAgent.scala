package io.scalac.mesmer.agent.akka.http

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.akka._
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.AkkaHttpModule

object AkkaHttpAgent
    extends InstrumentModuleFactory(AkkaHttpModule)
    with AkkaHttpModule.AkkaHttpConnectionsMetricsDef[AkkaHttpModule.Jars[Version] => Agent]
    with AkkaHttpModule.AkkaHttpRequestMetricsDef[AkkaHttpModule.Jars[Version] => Agent] {

  private val supportedHttpVersions = version101x.or(version102x)

  private def ifSupported(versions: AkkaHttpModule.Jars[Version])(agent: => Agent): Agent = {
    import versions._
    if (
      version26x.supports(akkaActor) && version26x.supports(akkaActorTyped) && supportedHttpVersions.supports(akkaHttp)
    )
      agent
    else Agent.empty
  }

  val requestTime: AkkaHttpModule.AkkaHttpJars[Version] => Agent =
    versions => ifSupported(versions)(requestEvents)

  val requestCounter: AkkaHttpModule.AkkaHttpJars[Version] => Agent = versions => ifSupported(versions)(requestEvents)

  val connections: AkkaHttpModule.AkkaHttpJars[Version] => Agent = versions => ifSupported(versions)(connectionEvents)

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

  def agent: Agent = {
    def orEmpty(condition: Boolean, agent: Agent): Agent = if (condition) agent else Agent.empty
    val configuration: AkkaHttpModule.Config             = module.enabled

    orEmpty(configuration.connections, connectionEvents) ++
    orEmpty(configuration.requestTime, requestEvents) ++
    orEmpty(configuration.requestCounter, requestEvents)
  }

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Some if feature can be enabled, None otherwise
   */
  def agent(
    jars: AkkaHttpModule.AkkaHttpJars[Version]
  ): Agent = {
    import module.enabled
    val requestCounterAgent = if (enabled.requestCounter) requestCounter(jars) else Agent.empty
    val requestTimeAgent    = if (enabled.requestTime) requestTime(jars) else Agent.empty
    val connectionsAgent    = if (enabled.connections) connections(jars) else Agent.empty

    val resultantAgent = requestCounterAgent ++ requestTimeAgent ++ connectionsAgent

    resultantAgent
  }

}
