package io.scalac.mesmer.agent.akka.dispatcher

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.module.AkkaDispatcherModule
import io.scalac.mesmer.core.module.AkkaDispatcherModule.AkkaDispatcherMinMaxThreadsConfigMetricsDef
import io.scalac.mesmer.core.module.AkkaDispatcherModule.AkkaDispatcherThreadCountMetricsDef

object AkkaDispatcherAgent
    extends InstrumentModuleFactory(AkkaDispatcherModule)
    with AkkaDispatcherMinMaxThreadsConfigMetricsDef[Agent]
    with AkkaDispatcherThreadCountMetricsDef[Agent] {

  def agent: Agent = {

    val config = module.enabled

    List(
      minThreads.onCondition(config.minThreads),
      maxThreads.onCondition(config.maxThreads),
      parallelismFactor.onCondition(config.parallelismFactor),
      activeThreads.onCondition(config.activeThreads),
      totalThreads.onCondition(config.totalThreads)
    ).reduce(_ ++ _)
  }

  private lazy val dispatcherConfigEvent =
    Agent(
      instrument("akka.actor.ActorCell".fqcn)
        .visit(ActorCellConstructorAdvice, constructor)
    )
  private lazy val dispatcherExecuteTaskEvent =
    Agent(
      instrument("akka.dispatch.Dispatcher".fqcn)
        .visit(DispatcherExecuteTaskAdvice, "executeTask")
    )

  val minThreads = dispatcherConfigEvent

  val maxThreads = dispatcherConfigEvent

  val parallelismFactor = dispatcherConfigEvent

  val totalThreads = dispatcherExecuteTaskEvent

  val activeThreads = dispatcherExecuteTaskEvent
}
