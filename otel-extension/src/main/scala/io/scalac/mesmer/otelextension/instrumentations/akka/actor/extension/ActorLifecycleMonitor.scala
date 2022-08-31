package io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension

import akka.actor.typed
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorRefOps

import io.scalac.mesmer.otelextension.instrumentations.akka.actor.InstrumentsProvider
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.ActorLifecycleEvents.ActorCreated
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.ActorLifecycleEvents.ActorTerminated

final class ActorLifecycleMonitor(context: ActorContext[ActorLifecycleEvents])
    extends AbstractBehavior[ActorLifecycleEvents](context) {
  override def onMessage(msg: ActorLifecycleEvents): Behavior[ActorLifecycleEvents] = msg match {
    case ActorCreated(ref) =>
      InstrumentsProvider.instance().actorsCreated.add(1)
      context.watchWith(ref.toTyped, ActorTerminated(ref))
      Behaviors.same
    case ActorTerminated(_) =>
      InstrumentsProvider.instance().actorsTerminated.add(1)
      Behaviors.same
  }
}

object ActorLifecycleMonitor {
  def apply(): Behavior[ActorLifecycleEvents] =
    Behaviors.setup[ActorLifecycleEvents](ctx => new ActorLifecycleMonitor(ctx))

  def subscribeToEventStream(system: typed.ActorSystem[_]): Unit = {
    val actor: typed.ActorRef[ActorLifecycleEvents] = createFromSystem(system)
    system.eventStream.tell(EventStream.Subscribe(actor))
  }

  private def createFromSystem(system: typed.ActorSystem[_]): typed.ActorRef[ActorLifecycleEvents] =
    system.systemActorOf(
      Behaviors.supervise(apply()).onFailure(SupervisorStrategy.restart),
      "mesmerActorLifecycleMonitor"
    )
}
