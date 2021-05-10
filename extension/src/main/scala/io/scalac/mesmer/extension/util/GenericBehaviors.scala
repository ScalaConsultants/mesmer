package io.scalac.mesmer.extension.util

import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.reflect.classTag

object GenericBehaviors {

  private case object Timeout

  /**
   * Creates behavior that waits for service to be accessible on [[ serviceKey ]]
   * After that it transition to specified behavior using [[ next ]] function as factory
   * @param serviceKey
   * @param next factory function creating target behavior
   * @tparam T
   * @tparam I
   * @return
   */
  def waitForService[T, I: ClassTag](serviceKey: ServiceKey[T], bufferSize: Int = 1024)(
    next: ActorRef[T] => Behavior[I]
  ): Behavior[I] = waitForServiceInternal(serviceKey, bufferSize, None)(next, Behaviors.stopped)

  def waitForServiceWithTimeout[T, I: ClassTag](
    serviceKey: ServiceKey[T],
    timeout: FiniteDuration,
    bufferSize: Int = 1024
  )(
    next: ActorRef[T] => Behavior[I],
    onTimeout: => Behavior[I]
  ): Behavior[I] = waitForServiceInternal(serviceKey, bufferSize, Some(timeout))(next, onTimeout)

  /**
   * Creates behavior that waits for service to be accessible on [[ serviceKey ]]
   * After that it transition to specified behavior using [[ next ]] function as factory
   * @param serviceKey
   * @param next factory function creating target behavior
   * @tparam T
   * @tparam I
   * @return
   */
  private def waitForServiceInternal[T, I: ClassTag](
    serviceKey: ServiceKey[T],
    bufferSize: Int,
    timeout: Option[FiniteDuration]
  )(
    next: ActorRef[T] => Behavior[I],
    onTimeout: => Behavior[I] = Behaviors.stopped
  ): Behavior[I] =
    Behaviors
      .setup[Any] { context => // use any to mimic union types
        import context._

        setLoggerName(classTag[I].runtimeClass)

        /*
            We use child actors instead of message adapters to avoid issues with clustered receptionist
         */
        val subscriptionChild = context.spawnAnonymous(Behaviors.receiveMessage[Listing] { listing =>
          listing.allServiceInstances(serviceKey).headOption.fold[Behavior[Listing]](Behaviors.same) { ref =>
            context.self ! ref
            Behaviors.stopped
          }
        })

        system.receptionist ! Receptionist.Subscribe(serviceKey, subscriptionChild)

        def waitingForService(cancelTimeout: Option[Cancellable]): Behavior[Any] =
          Behaviors.withStash(bufferSize) { buffer =>
            Behaviors.receiveMessagePartial {
              case ref: ActorRef[T] @unchecked =>
                cancelTimeout.foreach(_.cancel())
                buffer.unstashAll(next(ref).asInstanceOf[Behavior[Any]])
              case message: I =>
                buffer.stash(message)
                Behaviors.same
              case Timeout =>
                context.stop(subscriptionChild)
                buffer.unstashAll(
                  onTimeout
                    .transformMessages[Any] { // we must create interceptor that will filter all other messages that don't much inner type parameter
                      case message: I => message
                    }
                )
              case _ => Behaviors.unhandled
            }
          }

        val cancel = timeout.map(after => context.scheduleOnce(after, context.self, Timeout))
        waitingForService(cancel)
      }
      .narrow[I]
}
