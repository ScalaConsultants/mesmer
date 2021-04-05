package io.scalac.extension.service

import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Sink, Source, SourceQueueWithComplete }
import akka.{ actor => classic }
import com.typesafe.config.Config
import io.scalac.core.actorServiceKey
import io.scalac.core.event.ActorEvent
import io.scalac.core.model._
import io.scalac.extension.config.Configuration
import io.scalac.extension.metric.ActorSystemMonitor
import io.scalac.extension.metric.ActorSystemMonitor.Labels
import io.scalac.extension.service.DeltaActorTree._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

case class DeltaActorTreeConfig(
  bulkWithin: FiniteDuration
)

object DeltaActorTreeConfig extends Configuration[DeltaActorTreeConfig] {
  override def default: DeltaActorTreeConfig = DeltaActorTreeConfig(1.second)

  override protected val configurationBase: String = "io.scalac.akka-monitoring.actor"

  override protected def extractFromConfig(config: Config): DeltaActorTreeConfig =
    DeltaActorTreeConfig(
      config
        .tryValue("bulk-within")(_.getDuration)
        .map(_.toScala)
        .getOrElse(default.bulkWithin)
    )
}

object DeltaActorTree {

  sealed trait Api

  sealed trait Event extends Api

  private final case class ActorTerminated(actorRef: classic.ActorRef) extends Event
  private final case class ActorCreated(actorRef: classic.ActorRef)    extends Event

  sealed trait Command extends Api

  case class Subscribe(ref: ActorRef[Delta]) extends Command

  case class Unsubscribe(ref: ActorRef[Delta]) extends Command

  case class Delta(created: Seq[classic.ActorRef], terminated: Seq[classic.ActorRef])

  def apply(config: DeltaActorTreeConfig, monitor: ActorSystemMonitor, node: Option[Node]): Behavior[Command] =
    Behaviors.setup[Api](ctx => new DeltaActorTree(ctx, monitor, config, node)).narrow[Command]

  def apply(config: Config, monitor: ActorSystemMonitor, node: Option[Node]): Behavior[Command] =
    apply(DeltaActorTreeConfig.fromConfig(config), monitor, node)
}

final class DeltaActorTree(
  ctx: ActorContext[Api],
  monitor: ActorSystemMonitor,
  private val config: DeltaActorTreeConfig,
  node: Option[Node]
) extends AbstractBehavior[Api](ctx) {
  import context._

  private[this] val boundMonitor = monitor.bind(Labels(node))

  private[this] val subscribers = mutable.Map.empty[ActorRef[Delta], SourceQueueWithComplete[Event]]

  private[this] val actorTree = ListBuffer.empty[classic.ActorRef]

  private def init(): Unit =
    system.receptionist ! Register(
      actorServiceKey,
      context.messageAdapter[ActorEvent] { case ActorEvent.ActorCreated(ref, _) =>
        ActorCreated(ref)
      }
    )

  override def onMessage(msg: Api): Behavior[Api] = msg match {
    case created @ ActorCreated(ref) =>
      log.info("Actor created")
      context.watchWith(ref.toTyped, ActorTerminated(ref))
      actorTree += ref
      boundMonitor.createdActors.incValue(1L)

      subscribers.values.foreach(_.offer(created))
      Behaviors.same
    case terminated @ ActorTerminated(ref) =>
      log.info("Actor terminated")
      actorTree.subtractOne(ref)
      boundMonitor.terminatedActors.incValue(1L)
      subscribers.values.foreach(_.offer(terminated))

      Behaviors.same
    case Subscribe(subscriber) =>
      implicit val system = context.system

      if (!subscribers.contains(subscriber)) {
        log.info("New subscription {}", subscriber)

        val source = Source
          .queue[Event](100, OverflowStrategy.fail)
          .groupedWithin(100, config.bulkWithin)
          .map(_.foldLeft(Delta(Seq.empty, Seq.empty)) {
            case (acc, ActorCreated(ref))    => acc.copy(created = acc.created :+ ref)
            case (acc, ActorTerminated(ref)) => acc.copy(terminated = acc.terminated :+ ref)
          })
          .to(Sink.foreach(subscriber.tell))
          .run()

        subscribers += (subscriber -> source)
        offerCurrentStateAsEvents(source)
      }
      Behaviors.same
    case Unsubscribe(subsriber) =>
      subscribers
        .remove(subsriber)
        .fold {
          log.warn("Got unsubscribe but no subscriber found")
        }(_.complete())

      Behaviors.same
  }

  protected def offerCurrentStateAsEvents(source: SourceQueueWithComplete[Event]): Unit = {

    def publishOne(left: Seq[classic.ActorRef]): Future[Unit] = left match {
      case Seq() =>
        Future.successful(())
      case ref :: refs =>
        source
          .offer(ActorCreated(ref))
          .flatMap { _ =>
            publishOne(refs)
          }
    }

    publishOne(actorTree.toList)
  }

  init()
}
