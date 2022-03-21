package io.scalac.mesmer.agent.akka.dispatcher

import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ThreadPoolExecutor

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.dispatch.Dispatcher
import akka.dispatch.ExecutorServiceDelegate
import net.bytebuddy.asm.Advice.FieldValue
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.core.event.DispatcherEvent.ExecuteTaskEvent
import io.scalac.mesmer.core.event.EventBus

object DispatcherExecuteTaskAdvice {
  @OnMethodExit
  def executeTask(
    @This self: Any, // note: putting Dispatcher here instead of Any results to classloader exception
    @FieldValue("executorServiceDelegate") executorService: ExecutorServiceDelegate
  ): Unit =
    self match {
      case dispatcher: Dispatcher if dispatcher.id == "akka.actor.default-dispatcher" =>
        DispatcherDecorator.getActorSystem(dispatcher) match {
          case Some(system) =>
            val (executor, activeThreads, totalThreads) = executorService.executor match {
              case forkJoinPool: ForkJoinPool =>
                val active = forkJoinPool.getActiveThreadCount
                val total  = forkJoinPool.getPoolSize
                ("fork-join-pool", active, total)
              case threadPool: ThreadPoolExecutor =>
                val active = threadPool.getActiveCount
                val total  = threadPool.getPoolSize
                ("thread-pool-executor", active, total)
              case other =>
                val unsupportedExecutor = other.getClass.getSimpleName
                (unsupportedExecutor, -1, -1)
            }
            val event = ExecuteTaskEvent(executor, activeThreads, totalThreads)

            system.log.debug(s"Sending event: $event")
            EventBus(system.toTyped).publishEvent(event)
          case None =>
            println(s"No available actor system. Unable to publish event")
        }

      case _ =>
        println(s"Illegal argument: Not a dispatcher")
    }

}
