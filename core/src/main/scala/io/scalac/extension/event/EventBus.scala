package io.scalac.extension.event

import java.util.UUID

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Subscribe
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ OverflowStrategy, QueueOfferResult }
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.Success

trait EventBus extends Extension {
  def publishEvent[T <: MonitoredEvent: ClassTag](event: T)
}

object EventBus extends ExtensionId[EventBus] {
  final case class Event(timestamp: Long, event: MonitoredEvent)

  override def createExtension(system: ActorSystem[_]): EventBus = {
    implicit val s                = system
    implicit val timeout: Timeout = 1 second
    implicit val ec               = system.executionContext
    new ReceptionistBasedEventBus()
  }
}

object ReceptionistBasedEventBus {
  private final case class Subscribers(refs: Set[ActorRef[Any]])

  def cachingBehavior(implicit timeout: Timeout): Behavior[MonitoredEvent] = {

    def initialize(): Behavior[Any] = Behaviors.receive {
      //first message set up type of the service
      case (ctx, event: MonitoredEvent) => {
//        ctx.log.info("Received first event for service type")
//        val tag = ClassTag[event.Service](classOf[event.Service])
        Receptionist(ctx.system).ref ! Subscribe(
          event.serviceKey,
          ctx.messageAdapter { key =>
            val set = key.serviceInstances(event.serviceKey).filter(_.path.address.hasLocalScope)
            Subscribers(set.asInstanceOf[Set[ActorRef[Any]]])
          }
        )
        ctx.self ! event
        withCachedServices(Set.empty)
      }
    }

    // type safety should be guard by the stream
    def withCachedServices(services: Set[ActorRef[Any]]): Behavior[Any] =
      Behaviors.withStash(1024)(buffer =>
        Behaviors.receive {
          case (ctx, message) =>
            message match {
              case Subscribers(refs) => {
                ctx.log.info("Subscribers updated")
                buffer.unstashAll(withCachedServices(services ++ refs))
              }
              case event: MonitoredEvent if services.nonEmpty => {
                services.foreach(_.asInstanceOf[ActorRef[MonitoredEvent]] ! event)
                Behaviors.same
              }
              case event: MonitoredEvent => {
                ctx.log.warn("Recevied event but no services registered for this key")
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

    initialize().narrow[MonitoredEvent]
  }

}

private[event] class ReceptionistBasedEventBus(
  implicit val system: ActorSystem[_],
  val ec: ExecutionContext,
  val timeout: Timeout
) extends EventBus {
  import ReceptionistBasedEventBus._
  import system.log

  override def publishEvent[T <: MonitoredEvent: ClassTag](event: T): Unit =
    internalBus.offer(event).onComplete {
      case Success(QueueOfferResult.Enqueued) => log.trace("Successfully enqueued event {}", event)
      case _                                  => log.error("Error occurred when enqueueing event {}", event)
    }

  private[this] lazy val internalBus = {

    Source
      .queue[MonitoredEvent](1024, OverflowStrategy.fail)
      .groupBy(Integer.MAX_VALUE, _.serviceKey)
      .to(Sink.lazySink { () =>
        lazy val cachingActor = system.systemActorOf(cachingBehavior, UUID.randomUUID().toString)
        Sink.foreach(cachingActor ! _)
      })
      .run()
  }
}
