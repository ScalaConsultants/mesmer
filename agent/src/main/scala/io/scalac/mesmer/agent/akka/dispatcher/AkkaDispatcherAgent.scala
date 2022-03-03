package io.scalac.mesmer.agent.akka.dispatcher

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.akka.version26x
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.{AkkaActorModule, AkkaDispatcherModule}

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
  ): (Agent, AkkaDispatcherModule.Metrics[Boolean]) = ???

  private def ifSupported(versions: AkkaActorModule.Jars[Version])(agent: => Agent): Option[Agent] =
    if (version26x.supports(versions.akkaActor) && version26x.supports(versions.akkaActorTyped)) {
      Some(agent)
    } else None

  private lazy val loadDispatcherConfigEvent =
    Agent(instrument("akka.dispatch.ExecutorServiceConfigurator".fqcn).visit(ExecutorServiceConfiguratorConstructorAdvice, constructor))

  val minThreads: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] = ???

  val maxThreads: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] = ???

  val parallelismFactor: AkkaDispatcherModule.AkkaDispatcherJars[Version] => Option[Agent] = ???
}
