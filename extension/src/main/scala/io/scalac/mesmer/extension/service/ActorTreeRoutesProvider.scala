package io.scalac.mesmer.extension.service
import akka.actor.ExtendedActorSystem
import akka.actor.setup.Setup
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, Scheduler }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.management.scaladsl.{ ManagementRouteProvider, ManagementRouteProviderSettings }
import akka.util.Timeout
import akka.{ actor, actor => classic }
import io.scalac.mesmer.core.model.Tag
import io.scalac.mesmer.extension.service.ActorTreeService.Command.GetActors
import io.scalac.mesmer.extension.util.GenericBehaviors
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.math.PartialOrdering
import scala.util._
import scala.util.control.NoStackTrace

sealed trait Tree[+T] {
  def insert[B >: T](element: B)(implicit ordering: PartialOrdering[B]): Tree[B]
}

case object Leaf extends Tree[Nothing] {
  def insert[B >: Nothing](element: B)(implicit ordering: PartialOrdering[B]): Tree[B] = Tree.empty(element)
}

final case class Node[T] private (value: T, children: Seq[Tree[T]]) extends Tree[T] {
  final def insert[B >: T](element: B)(implicit ordering: PartialOrdering[B]): Tree[B] =
    ordering.tryCompare(element, value) match {
      case Some(x) if x < 0 => Tree.withChildren(element)(this)
      case Some(x) if x > 0 => this.copy(children = children.map(_.insert(element)))
      case _                => this // we are not connected by any edge
    }
}

object Tree {
  def empty[T](value: T): Tree[T]                            = Node(value, Seq(Leaf)) // there must be always one leaf present
  def withChildren[T](value: T)(children: Tree[T]*): Tree[T] = Node(value, children)
  def leaf[T]: Tree[T]                                       = Leaf
}

private[service] object ActorTreeRoutesImpl {

  def routes(
    service: ActorRef[ActorTreeService.Command]
  )(implicit timeout: Timeout, scheduler: Scheduler, ec: ExecutionContext): Future[Tree[classic.ActorRef]] = {
    val flatTree = service ? ((ref: ActorRef[Seq[classic.ActorRef]]) => GetActors(Tag.all, ref))

    flatTree.map(createTree)
  }

  implicit val actorRefPartialOrdering: PartialOrdering[classic.ActorRef] = new PartialOrdering[actor.ActorRef] {
    def tryCompare(x: actor.ActorRef, y: actor.ActorRef): Option[Int] =
      (x.path.toStringWithoutAddress, y.path.toStringWithoutAddress) match {
        case (xPath, yPath) if xPath == yPath          => Some(0)
        case (xPath, yPath) if xPath.startsWith(yPath) => Some(-1)
        case (xPath, yPath) if yPath.startsWith(xPath) => Some(1)
        case _                                         => None
      }

    def lteq(x: actor.ActorRef, y: actor.ActorRef): Boolean = actorLevel(x) <= actorLevel(y)
  }

  private def actorLevel(ref: classic.ActorRef): Int = ref.path.toStringWithoutAddress.count(_ == '/')

  private def createTree(seq: Seq[classic.ActorRef]): Tree[classic.ActorRef] =
    seq
      .sortBy(actorLevel)
      .foldLeft(Tree.leaf[classic.ActorRef])(_.insert(_))
}

final case class ActorTreeRoutesProviderConfig(timeout: FiniteDuration)

private[scalac] sealed trait ActorTreeServiceSetup extends Setup {
  def actorService(timeout: FiniteDuration)(implicit
    system: ExtendedActorSystem
  ): Future[ActorRef[ActorTreeService.Command]]
}

object ActorTreeServiceSetup {
  private[service] def receptionist: ActorTreeServiceSetup = new ActorTreeServiceSetup {
    def actorService(
      timeout: FiniteDuration
    )(implicit system: ExtendedActorSystem): Future[ActorRef[ActorTreeService.Command]] = {

      val promise = Promise[ActorRef[ActorTreeService.Command]]()

      system.toTyped.systemActorOf(
        GenericBehaviors.waitForServiceWithTimeout(actorTreeServiceKey, timeout)(
          ref =>
            Behaviors.setup[Nothing] { _ =>
              promise.success(ref)
              Behaviors.stopped
            },
          Behaviors.setup[Nothing] { _ =>
            promise.failure(ServiceDiscoveryTimeout)
            Behaviors.stopped
          }
        ),
        "mesmer-actor-tree-routes-provider-service-discovery"
      )

      promise.future
    }
  }

  private[service] def lift(ref: ActorRef[ActorTreeService.Command]): ActorTreeServiceSetup =
    new ActorTreeServiceSetup {
      def actorService(
        timeout: FiniteDuration
      )(implicit system: ExtendedActorSystem): Future[ActorRef[ActorTreeService.Command]] =
        Future.successful(ref)
    }

  case object ServiceDiscoveryTimeout extends Throwable with NoStackTrace
}

final class ActorTreeRoutesProvider(system: ExtendedActorSystem) extends ManagementRouteProvider {

  private val logger = LoggerFactory.getLogger(this.getClass)

  import ActorTreeServiceSetup._
  import system.dispatcher

  private val settings: ActorTreeRoutesProviderConfig = ??? //TODO add settings
  implicit val timeout: Timeout                       = settings.timeout

  lazy val stream: Future[ActorRef[ActorTreeService.Command]] =
    system.settings.setup.get[ActorTreeServiceSetup].getOrElse(receptionist).actorService(settings.timeout)(system)

  def routes(settings: ManagementRouteProviderSettings): Route = get {
    implicit val scheduler = system.toTyped.schedulers

    onComplete(stream.flatMap(ActorTreeRoutesImpl.routes)) {
      case Success(value) => complete(value) // TODO add serialzation
      case Failure(ServiceDiscoveryTimeout) =>
        logger.error("Actor service unavailable")
        complete(StatusCodes.ServiceUnavailable)
      case Failure(ex) =>
        logger.error("Unexpected error occurred", ex)
        complete(StatusCodes.InternalServerError)
    }
  }
}
