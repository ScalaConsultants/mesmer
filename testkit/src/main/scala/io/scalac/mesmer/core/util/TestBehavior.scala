package io.scalac.mesmer.core.util

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors

import scala.util.control.NoStackTrace

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

  object Pass {

    def registerService[A](serviceKey: ServiceKey[A], ref: ActorRef[A]): Behavior[A] =
      Behaviors.setup[A] { context =>
        context.system.receptionist ! Register(serviceKey, context.self)
        Behaviors.receiveMessage[A] { case event =>
          ref ! event
          Behaviors.same
        }
      }

    def toRef[A](ref: ActorRef[A]): Behavior[A] = Behaviors.receiveMessage { case message =>
      ref ! message
      Behaviors.same
    }
  }

  object Failing {
    final case object ExpectedFailure extends RuntimeException("Expected failure happened") with NoStackTrace

    def apply[A](): Behavior[A] =
      Behaviors
        .receiveMessage[Any] { _ =>
          throw ExpectedFailure
        }
        .narrow[A]

    def classic: Props = Props(new ClassicFailingActor)

    private class ClassicFailingActor extends Actor with ActorLogging {
      log.info("Failing actor initialized")
      override def receive: Receive = _ => throw ExpectedFailure
    }

  }
}
