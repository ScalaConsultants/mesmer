package io.scalac.agent.event
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Find
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ ActorSystem, Extension, ExtensionId }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ OverflowStrategy, QueueOfferResult }
import akka.util.Timeout
import io.scalac.extension.event.MonitoredEvent

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
  final case class Event(timestamp: Long, event: MonitoredEvent)
}

private[event] class ReceptionistBasedEventBus(
  implicit val system: ActorSystem[_],
  val ec: ExecutionContext,
  val timeout: Timeout
) extends EventBus {
  import ReceptionistBasedEventBus._
  import system.log

  override def publishEvent[T <: MonitoredEvent: ClassTag](event: T): Unit = {
    val timestamp = System.currentTimeMillis()
    internalBus.offer(Event(timestamp, event)).onComplete {
      case Success(QueueOfferResult.Enqueued) => log.trace("Successfully enqueued event {}", event)
      case _                                  => log.error("Error occurred when enqueueing event {}", event)
    }
  }

  private[this] lazy val internalBus =
    Source
      .queue[Event](1024, OverflowStrategy.fail)
      .to(Sink.foreachAsync(1) {
        case Event(_, event) =>
          (Receptionist(system).ref ? Find(event.serviceKey))
            .map(_.serviceInstances(event.serviceKey).filter(_.path.address.hasLocalScope).foreach { ref =>
              ref ! event.asInstanceOf[event.Service]
            })
      })
      .run()
}
