package io.scalac.extension.util

import akka.actor.Status.Failure
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior, Terminated }

object TerminationRegistry {

  sealed trait Command

  final case class Watch(ref: ActorRef[_], replyTo: Option[ActorRef[Ack]]) extends Command

  final case class WaitForTermination(ref: ActorRef[_], replyTo: ActorRef[Ack]) extends Command

  private[util] case object UnwatchAll extends Command

  private[util] case object UnwatchAllException
      extends RuntimeException("Unwatch all command was issued while waiting for termination")

  sealed trait Ack extends Command

  private[this] case object AkcImpl extends Ack

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    def watch(
      watched: Set[ActorRef[_]],
      waitFor: Map[ActorRef[_], ActorRef[Ack]],
      terminated: Set[ActorRef[_]]
    ): Behavior[Command] =
      Behaviors
        .receiveMessage[Command] {
          case Watch(ref, replyTo) =>
            if (watched.contains(ref)) {
              ctx.log.warn(s"Already watching actor ${ref} ")
              replyTo.foreach(_ ! AkcImpl)
              Behaviors.same
            } else {
              ctx.log.debug(s"Start watching ${ref}")
              ctx.watch(ref)
              replyTo.foreach(_ ! AkcImpl)
              watch(watched + ref, waitFor, terminated)
            }
          case WaitForTermination(ref, replyTo) =>
            ctx.log.debug(s"Wait for termination of ${ref}")
            if (terminated.contains(ref)) {
              replyTo ! AkcImpl
              watch(watched, waitFor, terminated - ref)
            } else {
              if (!watched.contains(ref)) {
                ctx.watch(ref)
              }
              watch(watched - ref, waitFor + (ref -> replyTo), terminated)
            }
          case UnwatchAll =>
            ctx.log.debug("Unwatch all actors and clears state")
            val all = watched ++ waitFor.keySet
            all.foreach(ctx.unwatch)
            waitFor.values.foreach(_.unsafeUpcast[Any] ! Failure(UnwatchAllException))
            watch(Set.empty, Map.empty, Set.empty)
          case AkcImpl =>
            ctx.log.debug("Ack received")
            Behaviors.same
        }
        .receiveSignal {
          case (_, Terminated(ref)) if watched.contains(ref) => {
            ctx.log.debug(s"Actor ${ref} terminated")
            watch(watched - ref, waitFor, terminated + ref)
          }
          case (_, Terminated(ref)) if waitFor.keySet.contains(ref) => {
            ctx.log.debug(s"Actor ${ref} terminated")
            waitFor.get(ref).foreach(_ ! AkcImpl)
            watch(watched, waitFor - ref, terminated)
          }
        }
    watch(Set.empty, Map.empty, Set.empty)
  }

}
