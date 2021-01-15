package io.scalac.core.util

import akka.actor.{ ActorRef, ActorSystem, ExtendedActorSystem }
import io.scalac.core.model.ActorNode
import org.slf4j.LoggerFactory

import java.lang.invoke.MethodHandles._
import java.lang.invoke.MethodType.methodType
import java.lang.invoke.{ MethodHandle, MethodHandles }
import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.control.NonFatal
class ActorDiscovery
object ActorDiscovery {

  private val logger = LoggerFactory.getLogger(ActorDiscovery.getClass)

  private val actorRefWithCell: Class[_] = Class.forName("akka.actor.ActorRefWithCell")

  private val cell = Class.forName("akka.actor.Cell")

  private val childrenContainer = Class.forName("akka.actor.dungeon.ChildrenContainer")

  private val actorRefScope = Class.forName("akka.actor.ActorRefScope")

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

  def rethrowFatal(log: String, throwable: Throwable): immutable.Iterable[ActorRef] = throwable match {
    case NonFatal(ex) => {
      logger.error(log, ex)
      immutable.Iterable.empty
    }
    case fatal => throw fatal
  }

  val extractChildrenSafe: MethodHandle = {

    val nonFatalHandle = insertArguments(
      lookup()
        .findStatic(
          classOf[ActorDiscovery],
          "rethrowFatal",
          methodType(classOf[immutable.Iterable[ActorRef]], classOf[String], classOf[Throwable])
        ),
      0,
      "Exception while collecting actor children"
    )

    catchException(extractChildren, classOf[Throwable], nonFatalHandle)
  }

  private val isLocalHandle: MethodHandle = {
    lookup().findVirtual(actorRefScope, "isLocal", methodType(classOf[Boolean]))
  }

  def isLocal(ref: ActorRef): Boolean = isLocalHandle.invoke(ref)

  def getUserActorsFlat(implicit system: ExtendedActorSystem): Seq[ActorNode] = getActorsFrom(system.provider.guardian)

  def getActorsFrom(from: ActorRef)(implicit system: ActorSystem): Seq[ActorNode] = {
    def getChildren(ref: ActorRef): Seq[ActorRef] =
      extractChildrenSafe.invoke(ref).asInstanceOf[immutable.Iterable[ActorRef]].toSeq
    @tailrec
    def flatActorsStructure(check: Seq[ActorRef], acc: Seq[ActorNode]): Seq[ActorNode] = check match {
      case Seq() => acc
      case head +: tail => {
        val children = getChildren(head).filter(isLocal)
        flatActorsStructure(children ++ tail, acc ++ children.map(ref => ActorNode(head.path, ref.path)))
      }
    }

    flatActorsStructure(List(from), Nil)
  }

  def allActorsFlat(implicit system: ExtendedActorSystem): Seq[ActorNode] = getActorsFrom(system.provider.rootGuardian)

}
