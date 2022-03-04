package io.scalac.mesmer.agent.akka.dispatcher

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.dispatch.DispatcherPrerequisites
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodEnter

import io.scalac.mesmer.core.event.DispatcherEvent.SetDefaultExecutorConfig
import io.scalac.mesmer.core.event.EventBus

object ExecutorServiceConfiguratorConstructorAdvice {

  @OnMethodEnter
  def constructor(
    @Argument(0) config: com.typesafe.config.Config,
    @Argument(1) dispatcherPrerequisites: DispatcherPrerequisites
  ): Unit =
    // TODO confirm if referencing deadLetterMailbox.actor is safe because it is marked as @volatile
    Option(dispatcherPrerequisites.mailboxes.deadLetterMailbox.actor).foreach { actor =>
      val system = actor.system
      val event = config.getString("akka.default-dispatcher.executor") match {
        case "thread-pool-executor" =>
          val executorConfig = config.getConfig(s"akka.default-dispatcher.thread-pool-executor")
          SetDefaultExecutorConfig(
            minThreads = executorConfig.getInt("core-pool-size-min"),
            maxThreads = executorConfig.getInt("core-pool-size-max"),
            parallelismFactor = executorConfig.getDouble("core-pool-size-factor")
          )
        case executor =>
          val executorConfig = config.getConfig(s"akka.default-dispatcher.$executor")
          SetDefaultExecutorConfig(
            minThreads = executorConfig.getInt("parallelism-min"),
            maxThreads = executorConfig.getInt("parallelism-max"),
            parallelismFactor = executorConfig.getDouble("parallelism-factor")
          )
      }
      EventBus(system.toTyped).publishEvent(event)
    }

}
