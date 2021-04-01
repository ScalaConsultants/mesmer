package io.scalac.core.event

import java.util.UUID

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Subscribe
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

import io.scalac.core.util.MutableTypedMap

trait EventBus extends Extension {
  def publishEvent[T <: AbstractEvent](event: T)(implicit service: Service[event.Service]): Unit
}

object EventBus extends ExtensionId[EventBus] {

  override def createExtension(system: ActorSystem[_]): EventBus = {
    implicit val s                = system
    implicit val timeout: Timeout = 1 second
    implicit val ec               = system.executionContext
    new ReceptionistBasedEventBus()
  }
}

object ReceptionistBasedEventBus {
  private final case class Subscribers(refs: Set[ActorRef[Any]])

  def cachingBehavior[T](serviceKey: ServiceKey[T])(implicit timeout: Timeout): Behavior[T] =
    Behaviors
      .setup[Any] { ctx =>
        ctx.log.debug("Subscribe to service {}", serviceKey)
        Receptionist(ctx.system).ref ! Subscribe(
          serviceKey,
          ctx.messageAdapter { key =>
            val set = key.serviceInstances(serviceKey).filter(_.path.address.hasLocalScope)
            Subscribers(set.asInstanceOf[Set[ActorRef[Any]]])
          }
        )

        def withCachedServices(services: Set[ActorRef[T]]): Behavior[Any] =
          Behaviors.withStash(1024)(buffer =>
            Behaviors.receiveMessage {
              case Subscribers(refs) =>
                ctx.log.debug("Subscribers for service {} updated", serviceKey)
                buffer.unstashAll(withCachedServices(refs.asInstanceOf[Set[ActorRef[T]]]))
              case event: T @unchecked
                  if services.nonEmpty => // T is removed on runtime but placing it here make type downcast
                ctx.log.trace("Publish event for service {}", serviceKey)
                services.foreach(_ ! event)
                Behaviors.same
              case event =>
                ctx.log.warn("Received event but no services registered for key {}", serviceKey)
                buffer.stash(event)
                Behaviors.same
            }
          )
        withCachedServices(Set.empty)
      }
      .narrow[T] // this mimic well union types but might fail if interceptor are in place

}

private[scalac] class ReceptionistBasedEventBus(implicit
  val system: ActorSystem[_],
  val ec: ExecutionContext,
  val timeout: Timeout
) extends EventBus {
  import ReceptionistBasedEventBus._

  type ServiceMapFunc[K <: AbstractService] = ActorRef[K#ServiceType]

  private[this] val serviceBuffers = MutableTypedMap[AbstractService, ServiceMapFunc]

  def publishEvent[T <: AbstractEvent](event: T)(implicit service: Service[event.Service]): Unit = ref ! event

  @inline private def ref[S](implicit service: Service[S]): ActorRef[S] =
    serviceBuffers.getOrCreate(service) {
      system.log.debug("Initialize event buffer for service {}", service.serviceKey)
      system.systemActorOf(cachingBehavior(service.serviceKey), UUID.randomUUID().toString)
    }
}
