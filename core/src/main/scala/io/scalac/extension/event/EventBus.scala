package io.scalac.extension.event

import java.util.UUID

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Subscribe
import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import io.scalac.`extension`.{ httpService, persistenceService }

import scala.collection.mutable.{ Map => MutableMap }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag

trait EventBus extends Extension {
  def publishEvent[T <: Event[_]: ClassTag](event: T)
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

  private[this] val eventBuffers = MutableMap.empty[ServiceKey[_], ActorRef[_]]

  override def publishEvent[T <: Event[_]: ClassTag](event: T): Unit = event match {
    case event: PersistenceEvent => dispatchEvent(persistenceService, event)
    case event: HttpEvent        => dispatchEvent(httpService, event)
  }

  def dispatchEvent[P](serviceKey: ServiceKey[P], event: P): Unit =
    eventBuffers
      .get(serviceKey)
      .orElse(synchronized {
        eventBuffers
          .get(serviceKey)
          .fold[Option[ActorRef[_]]] {
            system.log.error("Initialize event buffer for service {}", serviceKey)
            val ref = system.systemActorOf(cachingBehavior(serviceKey), UUID.randomUUID().toString)

            eventBuffers += (serviceKey -> ref)
            Some(ref)
          }(ref => Some(ref))
      })
      .foreach(ref => ref.asInstanceOf[ActorRef[P]] ! event)
}
