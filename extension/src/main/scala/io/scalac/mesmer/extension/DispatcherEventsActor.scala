package io.scalac.mesmer.extension

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.receptionist.Receptionist.Deregister
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import io.scalac.mesmer.core.event.DispatcherEvent
import io.scalac.mesmer.core.event.DispatcherEvent.ExecutorConfigEvent
import io.scalac.mesmer.core.dispatcherServiceKey
import io.scalac.mesmer.core.model.Node
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

      Receptionist(ctx.system).ref ! Register(dispatcherServiceKey, ctx.messageAdapter(DispatcherEventWrapper.apply))

      val receptionistAdapter = ctx.messageAdapter(DispatcherEventWrapper.apply)

      def createDispatcherAttributes(event: ExecutorConfigEvent): DispatcherStaticMetricsMonitor.Attributes =
        DispatcherStaticMetricsMonitor.Attributes(node, event.minThreads, event.maxThreads, event.parallelismFactor)

      Behaviors
        .receiveMessagePartial[Event] {
          case DispatcherEventWrapper(event: ExecutorConfigEvent) =>
            dispatcherMetricMonitor.bind(createDispatcherAttributes(event))
            Behaviors.same
          case _=>
            Behaviors.same
        }.receiveSignal { case (_, PostStop) =>
        ctx.log.info("Dispatcher events monitor terminated")
        Receptionist(ctx.system).ref ! Deregister(dispatcherServiceKey, receptionistAdapter)
        Behaviors.same
      }
    }
}
