package io.scalac.extension.service

import akka.actor.typed._
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors }
import akka.{ actor => classic }
import io.scalac.core.model.{ Tag, _ }
import io.scalac.extension.metric.ActorSystemMonitor
import io.scalac.extension.service.ActorTreeService.{ ActorTreeUpdate, Command, GetActors }
import io.scalac.extension.service.DeltaActorTree.{ Connect, Delta }

import scala.annotation.unused
import scala.collection.mutable.ArrayBuffer

object ActorTreeService {

  sealed trait Command

  private final case class ActorTreeUpdate(delta: Delta) extends Command

  final case class GetActors(tags: Tag, reply: ActorRef[Seq[classic.ActorRef]]) extends Command

  def apply(actorSystemMonitor: ActorSystemMonitor, node: Option[Node]): Behavior[Command] =
    apply(DeltaActorTree(actorSystemMonitor, node))

  // for testing
  private[service] def apply(deltaActorTreeBehavior: Behavior[DeltaActorTree.Command]): Behavior[Command] =
    Behaviors.setup(ctx => new ActorTreeService(ctx, deltaActorTreeBehavior))
}

final class ActorTreeService(ctx: ActorContext[Command], deltaActorTreeBehavior: Behavior[DeltaActorTree.Command])
    extends AbstractBehavior[Command](ctx) {
  import context._

  private[this] val snapshot = ArrayBuffer.empty[classic.ActorRef]

  private[this] val connectionAdapter = context.messageAdapter[Delta](ActorTreeUpdate)

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case ActorTreeUpdate(Delta(created, terminated)) =>
      snapshot.subtractAll(terminated)
      snapshot ++= created
      log.debug("Local snapshot {}", snapshot.toSeq)
      Behaviors.same
    case GetActors(Tag.all, reply) =>
      reply ! snapshot.toSeq
      Behaviors.same
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = { case Terminated(_) => // we only watch one ref
    spawnDeltaActorTree(true)
    Behaviors.same
  }

  private def spawnDeltaActorTree(@unused failed: Boolean): Unit = {
    val ref = context.spawn(
      Behaviors
        .supervise(deltaActorTreeBehavior)
        .onFailure(SupervisorStrategy.stop), // we have to restart manually
      "mesmer-delta-actor-tree-"
    )

    ref ! Connect(connectionAdapter)
  }

  spawnDeltaActorTree(false)
}
