package io.scalac.mesmer.extension

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Deregister
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors

import io.scalac.mesmer.core.dispatcherServiceKey
import io.scalac.mesmer.core.event.DispatcherEvent
import io.scalac.mesmer.core.event.DispatcherEvent.ExecutorConfigEvent
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.extension.DispatcherEventsActor.Event.DispatcherEventWrapper
import io.scalac.mesmer.extension.metric.DispatcherStaticMetricsMonitor

object DispatcherEventsActor {

  sealed trait Event extends SerializableMessage

  object Event {
    private[extension] final case class DispatcherEventWrapper(event: DispatcherEvent) extends Event
  }

  def apply(
    dispatcherMetricMonitor: DispatcherStaticMetricsMonitor,
    node: Option[Node]
  ): Behavior[Event] =
    Behaviors.setup[Event] { ctx =>
      val receptionistAdapter = ctx.messageAdapter(DispatcherEventWrapper.apply)

      Receptionist(ctx.system).ref ! Register(dispatcherServiceKey, ctx.messageAdapter(DispatcherEventWrapper.apply))

      def createAttributes(event: ExecutorConfigEvent): DispatcherStaticMetricsMonitor.Attributes =
        DispatcherStaticMetricsMonitor.Attributes(
          node,
          minThreads = event.minThreads,
          maxThreads = event.maxThreads
        )

      Behaviors
        .receiveMessagePartial[Event] {
          case DispatcherEventWrapper(event: ExecutorConfigEvent) =>
            val monitorBoundary = createAttributes(event)
            val monitor         = dispatcherMetricMonitor.bind(monitorBoundary)
            monitor.minThreads.setValue(event.minThreads)
            monitor.maxThreads.setValue(event.maxThreads)
            monitor.parallelismFactor.setValue(
              event.parallelismFactor.toLong
            ) // TODO find out how to have metrics with different data types

            ctx.log.debug(
              s"Dispatcher metrics set to monitor: minThreads [${event.minThreads}], " +
                s"maxThreads [${event.maxThreads}], parallelismFactor [${event.parallelismFactor}]"
            )
            Behaviors.same
          case _ =>
            Behaviors.same
        }
        .receiveSignal { case (_, PostStop) =>
          ctx.log.info("Dispatcher events monitor terminated")
          Receptionist(ctx.system).ref ! Deregister(dispatcherServiceKey, receptionistAdapter)
          Behaviors.same
        }
    }
}
