package io.scalac.extension.service

import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Keep, Sink, Source, SourceQueueWithComplete }
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
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

case class DeltaActorTreeConfig(
  bulkWithin: FiniteDuration,
  bulkMaxSize: Int
)

object DeltaActorTreeConfig extends Configuration[DeltaActorTreeConfig] {
  override def default: DeltaActorTreeConfig = DeltaActorTreeConfig(1.second, 1000)

  override protected val configurationBase: String = "io.scalac.akka-monitoring.actor"

  override protected def extractFromConfig(config: Config): DeltaActorTreeConfig =
    DeltaActorTreeConfig(
      config
        .tryValue("bulk-within")(_.getDuration)
        .map(_.toScala)
        .getOrElse(default.bulkWithin),
      config
        .tryValue("bulk-max-size")(_.getInt)
        .getOrElse(default.bulkMaxSize)
    )
}

object DeltaActorTree {

  sealed trait Api

  sealed trait Event extends Api

  private final case class ActorTerminated(actorRef: classic.ActorRef) extends Event with DeltaBuilderCommand
  private final case class ActorCreated(actorRef: classic.ActorRef)    extends Event with DeltaBuilderCommand

  sealed trait Command extends Api

  final case class Subscribe(ref: ActorRef[Delta]) extends Command

  final case class Unsubscribe(ref: ActorRef[Delta]) extends Command

  final case class Delta(created: Seq[classic.ActorRef], terminated: Seq[classic.ActorRef]) {

    lazy val nonEmpty: Boolean = created.nonEmpty || terminated.nonEmpty
  }

  sealed trait DeltaBuilderCommand

  private final case class BulkStateChange(delta: Delta) extends DeltaBuilderCommand

  private[DeltaActorTree] final class DeltaBuilder private () {

    private[this] val created    = ListBuffer.empty[classic.ActorRef]
    private[this] val terminated = ListBuffer.empty[classic.ActorRef]

    def process(command: DeltaBuilderCommand): this.type = {
      command match {
        case BulkStateChange(delta) =>
          created ++= delta.created
          terminated ++= delta.terminated
        case ActorCreated(ref)    => created += ref
        case ActorTerminated(ref) => terminated += ref
      }
      this
    }

    def result: Delta =
      Delta(created.toSeq, terminated.toSeq)

  }

  object DeltaBuilder {
    def apply(): DeltaBuilder = new DeltaBuilder()
  }

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

  private[this] val subscribers = mutable.Map.empty[ActorRef[Delta], SourceQueueWithComplete[DeltaBuilderCommand]]

  //TODO rethink if actor tree should be a flat structure
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
      log.trace("Actor created")
      context.watchWith(ref.toTyped, ActorTerminated(ref))
      actorTree += ref
      boundMonitor.createdActors.incValue(1L)

      subscribers.values.foreach(_.offer(created))
      Behaviors.same
    case terminated @ ActorTerminated(ref) =>
      log.trace("Actor terminated")
      actorTree.subtractOne(ref)
      boundMonitor.terminatedActors.incValue(1L)
      subscribers.values.foreach(_.offer(terminated))

      Behaviors.same
    case Subscribe(subscriber) =>
      implicit val system = context.system

      if (!subscribers.contains(subscriber)) {
        log.debug("New subscription {}", subscriber)

        val initial = Delta(created = actorTree.toSeq, terminated = Seq.empty)

        val source =
          subscriberStream(initial)
            .to(Sink.foreach(subscriber.tell))
            .run()

        subscribers += (subscriber -> source)
      }
      Behaviors.same
    case Unsubscribe(subscriber) =>
      subscribers
        .remove(subscriber)
        .fold {
          log.warn("Got unsubscribe but no subscriber found")
        }(_.complete())

      Behaviors.same
  }

  private def subscriberStream(init: Delta): Source[Delta, SourceQueueWithComplete[DeltaBuilderCommand]] =
    Source
      .single(init)
      .map(BulkStateChange)
      .concatMat(
        Source
          .queue[DeltaBuilderCommand](100, OverflowStrategy.fail)
      )(Keep.right)
      .groupedWithin(100, config.bulkWithin)
      .map(_.foldLeft(DeltaBuilder()) { case (builder, event) =>
        builder.process(event)
      }.result)
      .filter(_.nonEmpty)

  init()
}
