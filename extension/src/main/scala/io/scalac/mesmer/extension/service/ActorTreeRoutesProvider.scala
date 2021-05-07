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
import akka.{ actor => classic }
import io.scalac.mesmer.core.model.Tag
import io.scalac.mesmer.extension.service.ActorTreeService.Command
import io.scalac.mesmer.extension.service.ActorTreeService.Command.GetActors
import io.scalac.mesmer.extension.util.GenericBehaviors
import org.slf4j.LoggerFactory
import zio.Chunk
import zio.json._
import zio.json.ast.Json

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.math.PartialOrdering
import scala.util._
import scala.util.control.NoStackTrace

final case class NonEmptyTree[+T] private (value: T, children: Seq[NonEmptyTree[T]]) {
  final def insert[B >: T](element: B)(implicit ordering: PartialOrdering[B]): NonEmptyTree[B] =
    ordering.tryCompare(element, value) match {
      case Some(x) if x < 0 => NonEmptyTree.withChildren(element)(this)
      case Some(x) if x > 0 =>
        val updateIndex = children.indexWhere(subtree => ordering.tryCompare(element, subtree.value).exists(_ > 0))
        if (updateIndex > -1) {
          this.copy(children = children.updated(updateIndex, children(updateIndex).insert(element)))
        } else this.copy(children = children :+ NonEmptyTree(element))

      case _ => this // we are not connected by any edge
    }

  //stack-safe fold
  def foldLeft[B](merge: (Seq[B], T) => B)(init: B): B = {
    @tailrec
    def loop(stack: List[Seq[NonEmptyTree[T]]], terminal: List[Int], results: List[B]): B =
      stack match {
        case _ :: Nil if results.nonEmpty => results.head // value left on stack is the solution
        case head :: tail =>
          val index = terminal.head

          if (index != head.size) { // not finished calculating layer
            val current = head(index)

            if (current.children.isEmpty) { // can transform this value eg Leaf case
              loop(stack, index + 1 :: terminal.tail, merge(Seq(init), current.value) :: results)
            } else {
              // unpack children on the stack
              loop(current.children :: stack, 0 :: terminal, results)
            }
          } else { // finished all children
            val (currentLayerResults, leftResults) = results.splitAt(index)
            val ancestorsTerminals                 = terminal.tail
            val parent                             = tail.head(ancestorsTerminals.head)
            val mergedValue                        = merge(currentLayerResults.reverse, parent.value)

            loop(
              tail,
              ancestorsTerminals.head + 1 :: ancestorsTerminals.tail,
              mergedValue :: leftResults
            )
          }
      }

    loop(List(Seq(this)), List(0), Nil)
  }
}

object NonEmptyTree {
  def apply[T](value: T): NonEmptyTree[T]                                    = NonEmptyTree(value, Seq.empty)
  def withChildren[T](value: T)(children: NonEmptyTree[T]*): NonEmptyTree[T] = NonEmptyTree(value, children.toSeq)

  def fromSeq[T](elements: Seq[T])(implicit ordering: PartialOrdering[T]): Option[NonEmptyTree[T]] =
    elements
      .sortWith(ordering.lteq) match {
      case Seq(head, tail @ _*) => Some(tail.foldLeft(NonEmptyTree(head))(_.insert(_)))
      case _                    => None
    }

}

private[service] object ActorTreeRoutesImpl {

  def routes(
    service: ActorRef[ActorTreeService.Command]
  )(implicit
    timeout: Timeout,
    scheduler: Scheduler,
    ec: ExecutionContext
  ): Future[Option[NonEmptyTree[classic.ActorRef]]] = {
    val flatTree = service ? ((ref: ActorRef[Seq[classic.ActorRef]]) => GetActors(Tag.all, ref))

    flatTree.map(NonEmptyTree.fromSeq[classic.ActorRef])
  }

  implicit val actorRefOrdering: PartialOrdering[classic.ActorRef] = new PartialOrdering[classic.ActorRef] {

    def tryCompare(x: classic.ActorRef, y: classic.ActorRef): Option[Int] =
      (x.path.toStringWithoutAddress, y.path.toStringWithoutAddress) match {
        case (xPath, yPath) if xPath == yPath          => Some(0)
        case (xPath, yPath) if xPath.startsWith(yPath) => Some(-1)
        case (xPath, yPath) if yPath.startsWith(xPath) => Some(1)
        case _                                         => None
      }

    def lteq(x: classic.ActorRef, y: classic.ActorRef): Boolean = actorLevel(x) <= actorLevel(y)
  }

  private def actorLevel(ref: classic.ActorRef): Int = ref.path.toStringWithoutAddress.count(_ == '/')
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
            Behaviors.setup[Command] { _ =>
              promise.success(ref)
              Behaviors.stopped
            },
          Behaviors.setup[Command] { _ =>
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

  implicit val actorTreeEncoder: JsonEncoder[NonEmptyTree[classic.ActorRef]] = JsonEncoder[Json].contramap {
    _.foldLeft[Json.Obj] { case (children, current) =>
      val childrenFields = children.foldLeft[Chunk[(String, Json)]](Chunk.empty)(_ ++ _.fields)
      Json.Obj(current.path.toStringWithoutAddress -> Json.Obj(childrenFields))
    }(Json.Obj())
  }

  private val logger = LoggerFactory.getLogger(this.getClass)

  import ActorTreeServiceSetup._
  import system.dispatcher

  private val settings: ActorTreeRoutesProviderConfig = ??? //TODO add settings
  implicit val timeout: Timeout                       = settings.timeout

  lazy val stream: Future[ActorRef[ActorTreeService.Command]] =
    system.settings.setup.get[ActorTreeServiceSetup].getOrElse(receptionist).actorService(settings.timeout)(system)

  def routes(settings: ManagementRouteProviderSettings): Route = get {
    implicit val scheduler: Scheduler = system.toTyped.scheduler

    onComplete(stream.flatMap(ActorTreeRoutesImpl.routes)) {
      case Success(value) => complete(StatusCodes.OK, value.toJson)
      case Failure(ServiceDiscoveryTimeout) =>
        logger.error("Actor service unavailable")
        complete(StatusCodes.ServiceUnavailable)
      case Failure(ex) =>
        logger.error("Unexpected error occurred", ex)
        complete(StatusCodes.InternalServerError)
    }
  }
}
