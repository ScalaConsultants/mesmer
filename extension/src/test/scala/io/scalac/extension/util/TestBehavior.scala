package io.scalac.extension.util

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object TestBehavior {

  sealed trait Command

  object Command {

    case object Create extends Command
    case object Stop   extends Command
  }
  import Command._
  def apply(id: String): Behavior[Command] = Behaviors.receiveMessage {
    case Create => Behaviors.same
    case Stop   => Behaviors.stopped
  }
}
