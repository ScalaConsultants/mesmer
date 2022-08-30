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

import io.scalac.mesmer.otelextension.instrumentations.akka.actor.ActorLifecycleEvent.ActorCreated
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.ActorLifecycleEvent.ActorTerminated

trait ActorLifecycleEvent

object ActorLifecycleEvent {
  final case class ActorCreated(ref: ActorRef)    extends ActorLifecycleEvent
  final case class ActorTerminated(ref: ActorRef) extends ActorLifecycleEvent
}

final class ActorLifecycleMetricsMonitor(context: ActorContext[ActorLifecycleEvent])
    extends AbstractBehavior[ActorLifecycleEvent](context) {
  override def onMessage(msg: ActorLifecycleEvent): Behavior[ActorLifecycleEvent] = msg match {
    case ActorCreated(ref) =>
      InstrumentsProvider.instance().actorsCreated.add(1)
      context.watchWith(ref.toTyped, ActorTerminated(ref))
      Behaviors.same
    case ActorTerminated(_) =>
      InstrumentsProvider.instance().actorsTerminated.add(1)
      Behaviors.same
  }
}

object ActorLifecycleMetricsMonitor {
  def apply(): Behavior[ActorLifecycleEvent] =
    Behaviors.setup[ActorLifecycleEvent](ctx => new ActorLifecycleMetricsMonitor(ctx))

  def subscribeToEventStream(system: typed.ActorSystem[_]): Unit = {
    val actor: typed.ActorRef[ActorLifecycleEvent] = createFromSystem(system)
    system.eventStream.tell(EventStream.Subscribe(actor))
  }

  private def createFromSystem(system: typed.ActorSystem[_]): typed.ActorRef[ActorLifecycleEvent] =
    system.systemActorOf(
      Behaviors.supervise(apply()).onFailure(SupervisorStrategy.restart),
      "mesmerActorLifecycleMonitor"
    )
}
