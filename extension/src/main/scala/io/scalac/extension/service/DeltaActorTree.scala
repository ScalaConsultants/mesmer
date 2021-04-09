package io.scalac.extension.service

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PreRestart
import akka.actor.typed.Signal
import akka.actor.typed.receptionist.Receptionist.Deregister
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.{ actor => classic }
import com.typesafe.config.Config

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

import io.scalac.core.actorServiceKey
import io.scalac.core.event.ActorEvent
import io.scalac.core.model._
import io.scalac.extension.config.Configuration
import io.scalac.extension.metric.ActorSystemMonitor
import io.scalac.extension.metric.ActorSystemMonitor.Labels
import io.scalac.extension.service.DeltaActorTree.Delta._
import io.scalac.extension.service.DeltaActorTree._

case class DeltaActorTreeConfig(
  bulkWithin: FiniteDuration,
  bulkMaxSize: Int,
  eventQueueSize: Int
)

object DeltaActorTreeConfig extends Configuration[DeltaActorTreeConfig] {
  override def default: DeltaActorTreeConfig = DeltaActorTreeConfig(1.second, 1000, 1000)

  override protected val configurationBase: String = "io.scalac.akka-monitoring.actor"

  override protected def extractFromConfig(config: Config): DeltaActorTreeConfig =
    DeltaActorTreeConfig(
      config
        .tryValue("bulk-within")(_.getDuration)
        .map(_.toScala)
        .getOrElse(default.bulkWithin),
      config
        .tryValue("bulk-max-size")(_.getInt)
        .getOrElse(default.bulkMaxSize),
      config
        .tryValue("event-queue-size")(_.getInt)
        .getOrElse(default.eventQueueSize)
    )
}

object DeltaActorTree {

  sealed trait Api extends Any

  private final case object ServiceRegistered extends Api

  sealed trait Event extends Any with Api

  private final case class ActorTerminated(actorRef: classic.ActorRef)
      extends AnyVal
      with Event
      with DeltaBuilderCommand

  private final case class ActorCreated(details: ActorRefDetails)  extends AnyVal with Event with DeltaBuilderCommand
  private final case class ActorRetagged(details: ActorRefDetails) extends AnyVal with Event with DeltaBuilderCommand

  sealed trait Command extends Api

  final case class Connect(ref: ActorRef[Delta]) extends Command

  object Delta {

    sealed trait DeltaBuilderCommand extends Any

    private[DeltaActorTree] final case class BulkStateChange(delta: Delta) extends AnyVal with DeltaBuilderCommand

    private[DeltaActorTree] final class DeltaBuilder private () {

      private[this] val created    = ListBuffer.empty[ActorRefDetails]
      private[this] val terminated = ListBuffer.empty[classic.ActorRef]
      private[this] val retagged   = ListBuffer.empty[ActorRefDetails]

      //TODO check if this forces object wrapping
      def process(command: DeltaBuilderCommand): this.type = {
        command match {
          case BulkStateChange(delta) =>
            created ++= delta.created
            terminated ++= delta.terminated
            retagged ++= delta.retagged
          case ActorCreated(details) =>
            created += details
          case ActorTerminated(ref) => terminated += ref
          case ActorRetagged(details) =>
            retagged += details
        }
        this
      }

      def result: Delta =
        Delta(created.toSeq, terminated.toSeq)

    }

    object DeltaBuilder {
      def apply(): DeltaBuilder = new DeltaBuilder()
    }

  }

  final case class Delta(
    created: Seq[ActorRefDetails],
    terminated: Seq[classic.ActorRef],
    retagged: Seq[ActorRefDetails] = Seq.empty
  ) {
    lazy val nonEmpty: Boolean = created.nonEmpty || terminated.nonEmpty
  }

  private[service] def apply(
    config: DeltaActorTreeConfig,
    monitor: ActorSystemMonitor,
    node: Option[Node],
    restart: Boolean,
    backoffTraverser: ActorTreeTraverser
  ): Behavior[Command] = Behaviors
    .setup[Api](context => new DeltaActorTree(context, monitor, config, node, backoffTraverser).start(restart))
    .narrow[Command]

  private[service] def apply(
    monitor: ActorSystemMonitor,
    node: Option[Node],
    restart: Boolean = false,
    backoffTraverser: ActorTreeTraverser = ReflectiveActorTreeTraverser
  ): Behavior[Command] =
    Behaviors
      .setup[Api](context =>
        new DeltaActorTree(
          context,
          monitor,
          DeltaActorTreeConfig.fromConfig(context.system.settings.config),
          node,
          backoffTraverser
        ).start(restart)
      )
      .narrow[Command]
}

final class DeltaActorTree private (
  context: ActorContext[Api],
  monitor: ActorSystemMonitor,
  config: DeltaActorTreeConfig,
  node: Option[Node],
  backoffTraverser: ActorTreeTraverser
) {
  import context._

  private[this] val boundMonitor = monitor.bind(Labels(node))

  private val registeredAdapter = context.messageAdapter[ActorEvent] {
    case created: ActorEvent.ActorCreated =>
      ActorCreated(created.details)
    case setTags: ActorEvent.SetTags => ActorRetagged(setTags.details)
  }

  def start(restart: Boolean = false): Behavior[Api] = {
    system.receptionist ! Register(
      actorServiceKey,
      registeredAdapter,
      context.messageAdapter(_ => ServiceRegistered)
    )
    waitForRegistration(restart)
  }

  private def waitForRegistration(restart: Boolean): Behavior[Api] = Behaviors.withStash(config.eventQueueSize) {
    stash =>
      Behaviors.receiveMessagePartial { case ServiceRegistered =>
        val snapshot =
          if (restart)
            backoffTraverser
              .getActorTreeFromRootGuardian(system.classicSystem)
              .map(ref => ActorRefDetails(ref, Set.empty))
          else Nil
        stash.unstashAll(waitingForConnection(snapshot))
      }
  }

  private def connected(connected: SourceQueueWithComplete[DeltaBuilderCommand]): Behavior[Api] =
    Behaviors.receiveMessagePartial[Api] {
      case created @ ActorCreated(details) =>
        import details._
        onCreated(ref)
        connected.offer(created)
        Behaviors.same
      case terminated @ ActorTerminated(ref) =>
        onTerminated(ref)
        connected.offer(terminated)
        Behaviors.same
    }

  private def waitingForConnection(snapshot: List[ActorRefDetails]): Behavior[Api] = Behaviors
    .receiveMessagePartial[Api] {
      case ActorCreated(details) =>
        import details._
        onCreated(ref)
        waitingForConnection(details :: snapshot)
      case ActorTerminated(ref) =>
        onTerminated(ref)
        waitingForConnection(snapshot.filter(_.ref != ref))
      case Connect(ref) =>
        implicit val system = context.system

        log.debug("Connect DeltaActorTree to {}", ref)

        val initial = Delta(created = snapshot, terminated = Seq.empty)
        log.debug("Propagate initial state {}", initial)

        val source =
          subscriberStream(initial)
            .to(Sink.foreach(ref.tell))
            .run()

        connected(source)
    }
    .receiveSignal(deregister)

  private def onCreated(ref: classic.ActorRef): Unit = {
    log.trace("Actor created {}", ref)
    context.watchWith(ref.toTyped, ActorTerminated(ref))
    boundMonitor.createdActors.incValue(1L)
  }

  private def onTerminated(ref: classic.ActorRef): Unit = {
    log.trace("Actor {} terminated", ref)
    boundMonitor.terminatedActors.incValue(1L)
  }

  private def subscriberStream(init: Delta): Source[Delta, SourceQueueWithComplete[DeltaBuilderCommand]] =
    Source
      .single(init)
      .filter(_.nonEmpty)
      .map(BulkStateChange)
      .concatMat(
        Source
          .queue[DeltaBuilderCommand](config.eventQueueSize, OverflowStrategy.fail)
      )(Keep.right)
      .groupedWithin(config.bulkMaxSize, config.bulkWithin)
      .map(_.foldLeft(DeltaBuilder()) { case (builder, event) =>
        builder.process(event)
      }.result)
      .filter(_.nonEmpty)

  private val deregister: PartialFunction[(ActorContext[Api], Signal), Behavior[Api]] = { case (_, PreRestart) =>
    system.receptionist ! Deregister(actorServiceKey, registeredAdapter)
    Behaviors.same
  }
}
