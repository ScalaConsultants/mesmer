package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import akka.actor.ActorRef
import akka.actor.typed
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorRefOps

import io.scalac.mesmer.otelextension.instrumentations.akka.actor.ActorEvent.ActorCreated
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.ActorEvent.ActorTerminated

trait ActorEvent

object ActorEvent {
  final case class ActorCreated(ref: ActorRef)    extends ActorEvent
  final case class ActorTerminated(ref: ActorRef) extends ActorEvent
}

object ActorSystemMetrics {
  def actorCreated(newActor: ActorRef): Unit = newActor ! ActorCreated(newActor)
}

final class ActorSystemMetricsBehavior(context: ActorContext[ActorEvent])
    extends AbstractBehavior[ActorEvent](context) {
  override def onMessage(msg: ActorEvent): Behavior[ActorEvent] = msg match {
    case ActorCreated(ref) =>
      InstrumentsProvider.instance().actorsCreated.add(1)
      context.watchWith(ref.toTyped, ActorTerminated(ref))
      Behaviors.same
    case ActorTerminated(_) =>
      InstrumentsProvider.instance().actorsTerminated.add(1)
      Behaviors.same
  }
}

object ActorSystemMetricsBehavior {
  def apply(): Behavior[ActorEvent] =
    Behaviors.setup[ActorEvent](ctx => new ActorSystemMetricsBehavior(ctx))

  def createFromSystem(system: typed.ActorSystem[_]): typed.ActorRef[ActorEvent] = {

    val actor = system.systemActorOf(
      Behaviors.supervise(apply()).onFailure(SupervisorStrategy.restart),
      "mesmerSystemMetricsMonitor"
    )
    actor
  }

  def subscribeToEventStream(system: typed.ActorSystem[_]): Unit = {
    val actor: typed.ActorRef[ActorEvent] = createFromSystem(system)
    system.eventStream.tell(EventStream.Subscribe(actor))
  }
}
