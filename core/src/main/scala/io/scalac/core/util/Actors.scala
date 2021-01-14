package io.scalac.core.util

import akka.actor.{ ActorRef, ExtendedActorSystem }
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.Try
object Actors {

  private val logger = LoggerFactory.getLogger(Actor.getClass)

  case class Actor(parent: String, self: String)

  private val actorRefWithCell: Class[_] = Class.forName("akka.actor.ActorRefWithCell")
  lazy val actorRefWithCellCellField = {
    val method = actorRefWithCell.getDeclaredMethod("underlying")
    method.setAccessible(true)
    method
  }
  lazy val cellChildrenRefsField = {
    val method = Class.forName("akka.actor.Cell").getDeclaredMethod("childrenRefs")
    method.setAccessible(true)
    method
  }
  lazy val childrenContainerChildren = {
    val method = Class.forName("akka.actor.dungeon.ChildrenContainer").getDeclaredMethod("children")
    method.setAccessible(true)
    method
  }

  private lazy val functionChain: List[Any => Any] =
    List(x => actorRefWithCellCellField.invoke(x), x => cellChildrenRefsField.invoke(x), x => childrenContainerChildren.invoke(x))

  def isLocalProvider(system: ExtendedActorSystem): Boolean = {

    // TODO use dynamicAccess
    val localClass = Try(Class.forName("akka.actor.LocalActorRefProvider")).toOption
    println(localClass.isDefined)
    localClass.exists(_.isInstance(system.provider))
  }

  def getActorsFlat(system: ExtendedActorSystem): Seq[Actor] = {
    def getChildren(ref: ActorRef): Seq[ActorRef] =
      try {
        Function
          .chain(functionChain)
          .apply(ref)
          .asInstanceOf[immutable.Iterable[ActorRef]]
          .toSeq
      } catch {
        case e: Throwable =>
          logger.error("Got error", e)
          Seq.empty
      }

    @tailrec
    def flatActorsStructure(check: Seq[ActorRef], acc: Seq[Actor]): Seq[Actor] = check match {
      case Seq() => acc
      case head +: tail => {
        val children = getChildren(head)
        flatActorsStructure(children ++ tail, acc ++ children.map(ref => Actor(head.path.toString, ref.path.toString)))
      }
    }

    if (isLocalProvider(system)) {
      val guardian = system.provider.rootGuardian

      logger.info("Starting")
      flatActorsStructure(List(guardian), Nil)
    } else {
      logger.warn("Not local actor provider")
      Nil
    }
  }

}
