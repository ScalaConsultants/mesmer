package io.scalac.mesmer.agent.akka.dispatcher

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.akka.version26x
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.AkkaDispatcherModule
import io.scalac.mesmer.core.module.AkkaDispatcherModule.AkkaDispatcherMinMaxThreadsConfigMetricsDef
import io.scalac.mesmer.core.module.AkkaDispatcherModule.AkkaDispatcherThreadCountMetricsDef
import io.scalac.mesmer.core.module.AkkaDispatcherModule.Impl

object AkkaDispatcherAgent
    extends InstrumentModuleFactory(AkkaDispatcherModule)
    with AkkaDispatcherMinMaxThreadsConfigMetricsDef[AkkaDispatcherModule.Jars[Version] => Option[Agent]]
    with AkkaDispatcherThreadCountMetricsDef[AkkaDispatcherModule.Jars[Version] => Option[Agent]] {

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
    val totalThreadsAgent      = if (config.totalThreads) totalThreads(jars) else None
    val activeThreadsAgent     = if (config.totalThreads) activeThreads(jars) else None
    val resultantAgent = minThreadsAgent.getOrElse(Agent.empty) ++
      maxThreadsAgent.getOrElse(Agent.empty) ++
      parallelismFactorAgent.getOrElse(Agent.empty) ++
      totalThreadsAgent.getOrElse(Agent.empty) ++
      activeThreadsAgent.getOrElse(Agent.empty)

    val enabled = Impl[Boolean](
      minThreads = minThreadsAgent.isDefined,
      maxThreads = maxThreadsAgent.isDefined,
      parallelismFactor = parallelismFactorAgent.isDefined,
      totalThreads = totalThreadsAgent.isDefined,
      activeThreads = activeThreadsAgent.isDefined
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
  private lazy val dispatcherExecuteTaskEvent =
    Agent(
      instrument("akka.dispatch.Dispatcher".fqcnWithTags("metrics"))
        .visit(DispatcherExecuteTaskAdvice, "executeTask")
    )

  val minThreads: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] =
    versions => ifSupported(versions)(dispatcherConfigEvent)

  val maxThreads: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] =
    versions => ifSupported(versions)(dispatcherConfigEvent)

  val parallelismFactor: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] =
    versions => ifSupported(versions)(dispatcherConfigEvent)

  val totalThreads: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] =
    versions => ifSupported(versions)(dispatcherExecuteTaskEvent)

  val activeThreads: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] =
    versions => ifSupported(versions)(dispatcherExecuteTaskEvent)
}
