package io.scalac.mesmer.agent.akka.dispatcher

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.dispatch.Dispatcher
import akka.dispatch.MessageDispatcher
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodEnter

import io.scalac.mesmer.core.event.DispatcherEvent.ExecutorConfigEvent
import io.scalac.mesmer.core.event.EventBus

object ActorCellConstructorAdvice {

  @OnMethodEnter
  def constructor(
    @Argument(0) system: akka.actor.ExtendedActorSystem,
    @Argument(3) dispatcher: MessageDispatcher
  ): Unit =
    if (dispatcher.id == "akka.actor.default-dispatcher") {
      DispatcherDecorator.setActorSystem(dispatcher.asInstanceOf[Dispatcher], system)

      val akkaConfig       = system.settings.config.getConfig("akka.actor")
      val dispatcherConfig = akkaConfig.getConfig("default-dispatcher")
      dispatcherConfig.getString("executor") match {
        case "thread-pool-executor" =>
          val executorConfig = dispatcherConfig.getConfig(s"thread-pool-executor")
          val event = ExecutorConfigEvent(
            executor = "thread-pool-executor",
            minThreads = executorConfig.getInt("core-pool-size-min"),
            maxThreads = executorConfig.getInt("core-pool-size-max"),
            parallelismFactor = executorConfig.getDouble("core-pool-size-factor")
          )
          EventBus(system.toTyped).publishEvent(event)
        case "default-executor" =>
          val executorConfig = dispatcherConfig.getConfig(s"fork-join-executor")
          val event = ExecutorConfigEvent(
            executor = "fork-join-executor",
            minThreads = executorConfig.getInt("parallelism-min"),
            maxThreads = executorConfig.getInt("parallelism-max"),
            parallelismFactor = executorConfig.getDouble("parallelism-factor")
          )
          EventBus(system.toTyped).publishEvent(event)
        case otherExecutor =>
          system.log.debug(s"Executor [$otherExecutor] not supported")
      }
    } else {
      system.log.debug(s"Dispatcher [${dispatcher.id}] ignored")
    }
}
