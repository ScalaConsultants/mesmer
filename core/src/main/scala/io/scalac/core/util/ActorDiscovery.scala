package io.scalac.core.util

import akka.actor.{ActorRef, ExtendedActorSystem}
import io.scalac.core.model.ActorNode
import org.slf4j.LoggerFactory

import java.lang.invoke.MethodHandles._
import java.lang.invoke.MethodType.methodType
import java.lang.invoke.{MethodHandle, MethodHandles}
import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.Try

object ActorDiscovery {

  private val logger = LoggerFactory.getLogger(ActorDiscovery.getClass)

  private val actorRefWithCell: Class[_] = Class.forName("akka.actor.ActorRefWithCell")

  private val cell = Class.forName("akka.actor.Cell")

  private val childrenContainer = Class.forName("akka.actor.dungeon.ChildrenContainer")

  val extractChildren: MethodHandle = {
    val lookup = MethodHandles.lookup()

    val underlying   = lookup.findVirtual(actorRefWithCell, "underlying", methodType(cell))
    val childrenRefs = lookup.findVirtual(cell, "childrenRefs", methodType(childrenContainer))
    val children     = lookup.findVirtual(childrenContainer, "children", methodType(classOf[immutable.Iterable[_]]))

    foldArguments(
      dropArguments(foldArguments(dropArguments(children, 1, cell), childrenRefs), 1, actorRefWithCell),
      underlying
    )
  }

  def isLocalProvider(system: ExtendedActorSystem): Boolean = {
    // TODO use dynamicAccess
    val localClass = Try(Class.forName("akka.actor.LocalActorRefProvider")).toOption
    println(localClass.isDefined)
    localClass.exists(_.isInstance(system.provider))
  }

  def getActorsFlat(system: ExtendedActorSystem): Seq[ActorNode] = {
    def getChildren(ref: ActorRef): Seq[ActorRef] =
      try {
        extractChildren.invoke(ref).asInstanceOf[immutable.Iterable[ActorRef]].toSeq
      } catch {
        case e: Throwable =>
          logger.error("Got error", e)
          Seq.empty
      }

    @tailrec
    def flatActorsStructure(check: Seq[ActorRef], acc: Seq[ActorNode]): Seq[ActorNode] = check match {
      case Seq() => acc
      case head +: tail => {
        val children = getChildren(head)
        flatActorsStructure(children ++ tail, acc ++ children.map(ref => ActorNode(head.path, ref.path)))
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
