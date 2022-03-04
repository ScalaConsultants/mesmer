package io.scalac.mesmer.agent.akka.dispatcher

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.akka.version26x
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.AkkaDispatcherModule
import io.scalac.mesmer.core.module.AkkaDispatcherModule.Impl

object AkkaDispatcherAgent
    extends InstrumentModuleFactory(AkkaDispatcherModule)
    with AkkaDispatcherModule.Metrics[AkkaDispatcherModule.Jars[Version] => Option[Agent]] {

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Resulting agent and resulting configuration based on runtime properties
   */
  override protected def agent(
    config: AkkaDispatcherModule.Metrics[Boolean],
    jars: AkkaDispatcherModule.Jars[Version]
  ): (Agent, AkkaDispatcherModule.Metrics[Boolean]) = {

    val minThreadsAgent        = if (config.minThreads) minThreads(jars) else None
    val maxThreadsAgent        = if (config.maxThreads) maxThreads(jars) else None
    val parallelismFactorAgent = if (config.parallelismFactor) parallelismFactor(jars) else None
    val resultantAgent = minThreadsAgent.getOrElse(Agent.empty) ++
      maxThreadsAgent.getOrElse(Agent.empty) ++
      parallelismFactorAgent.getOrElse(Agent.empty)

    val enabled = Impl[Boolean](
      minThreads = minThreadsAgent.isDefined,
      maxThreads = maxThreadsAgent.isDefined,
      parallelismFactor = parallelismFactorAgent.isDefined
    )
    (resultantAgent, enabled)
  }

  private def ifSupported(versions: AkkaDispatcherModule.Jars[Version])(agent: => Agent): Option[Agent] =
    if (version26x.supports(versions.akkaActor) && version26x.supports(versions.akkaActorTyped)) {
      Some(agent)
    } else None

  private lazy val dispatcherConfigEvent =
    Agent(
      instrument("akka.dispatch.ExecutorServiceConfigurator".fqcn)
        .visit(ExecutorServiceConfiguratorConstructorAdvice, constructor)
    )

  val minThreads: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] =
    versions => ifSupported(versions)(dispatcherConfigEvent)

  val maxThreads: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] =
    versions => ifSupported(versions)(dispatcherConfigEvent)

  val parallelismFactor: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] =
    versions => ifSupported(versions)(dispatcherConfigEvent)
}
