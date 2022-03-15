package io.scalac.mesmer.agent.akka.dispatcher

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodEnter

import io.scalac.mesmer.core.event.DispatcherEvent.SetDefaultExecutorConfig
import io.scalac.mesmer.core.event.EventBus

object ActorCellConstructorAdvice {

  @OnMethodEnter
  def constructor(
    @Argument(0) system: akka.actor.ExtendedActorSystem
  ): Unit = {
    val akkaConfig       = system.settings.config.getConfig("akka.actor")
    val dispatcherConfig = akkaConfig.getConfig("default-dispatcher")

    val event = dispatcherConfig.getString("executor") match {
      case "thread-pool-executor" =>
        val executorConfig = dispatcherConfig.getConfig(s"thread-pool-executor")
        SetDefaultExecutorConfig(
          minThreads = executorConfig.getInt("core-pool-size-min"),
          maxThreads = executorConfig.getInt("core-pool-size-max"),
          parallelismFactor = executorConfig.getDouble("core-pool-size-factor")
        )
      case "default-executor" =>
        val executorConfig = dispatcherConfig.getConfig(s"fork-join-executor")
        SetDefaultExecutorConfig(
          minThreads = executorConfig.getInt("parallelism-min"),
          maxThreads = executorConfig.getInt("parallelism-max"),
          parallelismFactor = executorConfig.getDouble("parallelism-factor")
        )
      case otherExecutor =>
        throw new RuntimeException(s"$otherExecutor is not supported")
    }
    EventBus(system.toTyped).publishEvent(event)
  }
}
