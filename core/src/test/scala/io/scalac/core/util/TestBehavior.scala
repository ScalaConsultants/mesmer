package io.scalac.core.util

import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }

object TestBehavior {

  sealed trait Command

  object Command {

    case object Same extends Command
    case object Stop extends Command
  }
  import Command._
  def apply(id: String): Behavior[Command] = Behaviors.receiveMessage {
    case Same => Behaviors.same
    case Stop => Behaviors.stopped
  }
}

object TestBehaviors {

  object SameStop {

    sealed trait Command

    object Command {

      case object Same extends Command
      case object Stop extends Command
    }
    import Command._

    def apply(id: String): Behavior[Command] = Behaviors.receiveMessage {
      case Same => Behaviors.same
      case Stop => Behaviors.stopped
    }
  }

  object ReceptionistPass {

    def apply[A](serviceKey: ServiceKey[A], ref: ActorRef[A]): Behavior[A] =
      Behaviors.setup[A] { context =>
        context.system.receptionist ! Register(serviceKey, context.self)
        Behaviors.receiveMessage[A] { case event =>
          ref ! event
          Behaviors.same
        }
      }
  }


}


