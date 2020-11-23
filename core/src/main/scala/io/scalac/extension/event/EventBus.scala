package io.scalac.extension.event

import java.util.UUID

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Subscribe
import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import io.scalac.extension.util.TypedMap

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

trait EventBus extends Extension {
  def publishEvent[T <: AbstractEvent](event: T)(implicit serivce: Service[event.Service]): Unit
}

object EventBus extends ExtensionId[EventBus] {
  final case class Event(timestamp: Long, event: AbstractEvent)

  override def createExtension(system: ActorSystem[_]): EventBus = {
    implicit val s                = system
    implicit val timeout: Timeout = 1 second
    implicit val ec               = system.executionContext
    new ReceptionistBasedEventBus()
  }
}

object ReceptionistBasedEventBus {
  private final case class Subscribers(refs: Set[ActorRef[Any]])

  def cachingBehavior[T](serviceKey: ServiceKey[T])(implicit timeout: Timeout): Behavior[T] = {

    def initialize(): Behavior[Any] = Behaviors.receive {
      //first message set up type of the service
      case (ctx, event: T) => {
        ctx.log.error("Received first event for service {}", serviceKey)
        Receptionist(ctx.system).ref ! Subscribe(
          serviceKey,
          ctx.messageAdapter { key =>
            val set = key.serviceInstances(serviceKey).filter(_.path.address.hasLocalScope)
            Subscribers(set.asInstanceOf[Set[ActorRef[Any]]])
          }
        )
        ctx.self ! event
        withCachedServices(Set.empty)
      }
    }

    // type safety should be guard by the stream
    def withCachedServices(services: Set[ActorRef[T]]): Behavior[Any] =
      Behaviors.withStash(1024)(buffer =>
        Behaviors.receive {
          case (ctx, message) =>
            message match {
              case Subscribers(refs) => {
                ctx.log.info("Subscribers updated")
                buffer.unstashAll(withCachedServices(services ++ refs.asInstanceOf[Set[ActorRef[T]]]))
              }
              case event: T if services.nonEmpty => {
                services.foreach(_ ! event)
                Behaviors.same
              }
              case event: T => {
                ctx.log.warn("Received event but no services registered for this key")
                buffer.stash(event)
                Behaviors.same
              }
              case _ => {
                ctx.log.warn("Unhandled message")
                Behaviors.unhandled
              }
            }
        }
      )
    initialize().narrow[T]
  }

}

private[event] class ReceptionistBasedEventBus(
  implicit val system: ActorSystem[_],
  val ec: ExecutionContext,
  val timeout: Timeout
) extends EventBus {
  import ReceptionistBasedEventBus._

//  private[this] val eventBuffers = MutableMap.empty[ServiceKey[_], ActorRef[_]]

  type ServiceMapFunc[K <: AbstractService] = ActorRef[K#ServiceType]

  private[this] var serviceBuffers = TypedMap[AbstractService, ServiceMapFunc]
  override def publishEvent[T <: AbstractEvent](event: T)(implicit service: Service[event.Service]): Unit =
    getOrInitialize(service).foreach(_ ! event)

  private def getOrInitialize[P](service: Service[P]): Option[ActorRef[P]] =
    serviceBuffers
      .get(service)
      .orElse(
        synchronized {
          serviceBuffers
            .get(service)
            .fold {
              system.log.error("Initialize event buffer for service {}", service.serviceKey)
              val ref = system.systemActorOf(cachingBehavior(service.serviceKey), UUID.randomUUID().toString)
              serviceBuffers = serviceBuffers.insert(service)(ref)
              Some(ref)
            }(ref => Some(ref))
        }
      )
}
