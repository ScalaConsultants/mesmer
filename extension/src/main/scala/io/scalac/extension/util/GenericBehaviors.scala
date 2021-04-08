package io.scalac.extension.util

import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }

import scala.reflect.ClassTag

object GenericBehaviors {

  /**
   * Creates behavior that waits for service to be accessible on [[ serviceKey ]]
   * After that it transition to specified behavior using [[ next ]] function as factory
   * @param serviceKey
   * @param next factory function creating target behavior
   * @tparam T
   * @tparam I
   * @return
   */
  def waitForService[T, I: ClassTag](serviceKey: ServiceKey[T])(next: ActorRef[T] => Behavior[I]): Behavior[I] =
    Behaviors
      .setup[Any] { context => // use any to mimic union types
        import context._

        def start(): Behavior[Any] = {

          val adapter = context.messageAdapter[Listing](identity)

          system.receptionist ! Receptionist.Subscribe(serviceKey, adapter)
          waitingForService()
        }

        def waitingForService(): Behavior[Any] =
          Behaviors.withStash(1024) { buffer =>
            Behaviors.receiveMessagePartial {
              case listing: Listing =>
                listing
                  .serviceInstances(serviceKey)
                  .headOption
                  .fold[Behavior[Any]] {
                    log.debug("No service found")
                    Behaviors.same
                  } { actorTreeService =>
                    //                    context.stop(adapter) // hack to stop subscription
                    log.trace("Transition to inner behavior")
                    buffer.unstashAll(
                      next(actorTreeService)
                        .transformMessages[Any] { // we must create interceptor that will filter all other messages that don't much inner type parameter
                          case message: I => message
                        }
                    )

                  }
              case message: I =>
                buffer.stash(message)
                Behaviors.same
              case _ => Behaviors.unhandled
            }
          }

        start()
      }
      .narrow[I]
}
