package io.scalac.mesmer.agent.akka.http

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.akka._
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.core.module.AkkaHttpModule._
import io.scalac.mesmer.instrumentations.akka.http.HttpExtRequestsAdvice

object AkkaHttpAgent
    extends InstrumentModuleFactory(AkkaHttpModule)
    with AkkaHttpModule.AkkaHttpConnectionsMetricsDef[AkkaHttpModule.Jars[Version] => Option[Agent]]
    with AkkaHttpModule.AkkaHttpRequestMetricsDef[AkkaHttpModule.Jars[Version] => Option[Agent]] {

  private val supportedHttpVersions = version101x.or(version102x)

  private def ifSupported(versions: AkkaHttpModule.Jars[Version])(agent: => Agent): Option[Agent] = {
    import versions._
    if (
      version26x.supports(akkaActor) && version26x.supports(akkaActorTyped) && supportedHttpVersions.supports(akkaHttp)
    )
      Some(agent)
    else None
  }

  val requestTime: AkkaHttpModule.AkkaHttpJars[Version] => Option[Agent] =
    versions => ifSupported(versions)(requestEvents) // Version => Option[Agent]

  val requestCounter: AkkaHttpModule.AkkaHttpJars[Version] => Option[Agent] = versions =>
    ifSupported(versions)(requestEvents)

  val connections: AkkaHttpModule.AkkaHttpJars[Version] => Option[Agent] = versions =>
    ifSupported(versions)(connectionEvents)

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

  def agent(config: com.typesafe.config.Config): Agent = {
    def orEmpty(condition: Boolean, agent: Agent): Agent = if (condition) agent else Agent.empty
    val configuration: AkkaHttpModule.Config             = module.enabled(config)

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
  override def agent(
    config: AkkaHttpModule.All[Boolean],
    jars: AkkaHttpModule.AkkaHttpJars[Version]
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
