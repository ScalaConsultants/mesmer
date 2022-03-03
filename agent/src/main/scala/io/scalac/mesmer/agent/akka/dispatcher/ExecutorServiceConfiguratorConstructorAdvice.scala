package io.scalac.mesmer.agent.akka.dispatcher

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.dispatch.DispatcherPrerequisites
import io.scalac.mesmer.core.event.DispatcherEvent.SetDefaultExecutorConfig
import io.scalac.mesmer.core.event.EventBus
import net.bytebuddy.asm.Advice.{Argument, OnMethodEnter}

object ExecutorServiceConfiguratorConstructorAdvice {

  @OnMethodEnter
  def constructor(
    @Argument(0) config: com.typesafe.config.Config,
    @Argument(1) dispatcherPrerequisites: DispatcherPrerequisites
  ): Unit = {
    // TODO not sure if referencing deadLetterMailbox.actor is safe because it is marked as @volatile
    val system                = dispatcherPrerequisites.mailboxes.deadLetterMailbox.actor.system
    val defaultExecutorConfig = config.getConfig("akka.default-dispatcher.fork-join-executor")

    val event = SetDefaultExecutorConfig(
      minThreads = defaultExecutorConfig.getInt("parallelism-min"),
      maxThreads = defaultExecutorConfig.getInt("parallelism-max"),
      parallelismFactor = defaultExecutorConfig.getDouble("parallelism-factor")
    )
    EventBus(system.toTyped).publishEvent(event)
  }

}
