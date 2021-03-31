package io.scalac.agent.akka.actor

import akka.actor.{Actor, ActorContext}
import io.scalac.core.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice.{Argument, OnMethodExit, This}

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

class StashConstructorAdvice
object StashConstructorAdvice {

  @OnMethodExit
  def initStash(@This self: Actor): Unit =
    ActorCellDecorator
      .get(ClassicActorOps.getContext(self))
      .foreach(_.stashSize.initialize())

}

trait StashGetters {

  private lazy val lookup = MethodHandles.lookup()

  private lazy val stashSupportClass = Class.forName("akka.actor.StashSupport")

  // Disclaimer:  The way we access the stash vector of and StashSupport is a quite ugly because it's an private field.
  //              We discovered its name during the debug and we aren't sure if this pattern is consistent through the compiler variations and versions.
  private lazy val theStashMethodHandle =
  lookup.findVirtual(stashSupportClass, "akka$actor$StashSupport$$theStash", methodType(classOf[Vector[_]]))

  private lazy val getContextMethodHandle =
    lookup.findVirtual(stashSupportClass, "context", methodType(classOf[ActorContext]))

  @inline final def getStashSize(stashSupport: AnyRef): Int =
    theStashMethodHandle.invoke(stashSupport).asInstanceOf[Vector[_]].length

  @inline final def getActorCell(stashSupport: AnyRef): AnyRef =
    getContextMethodHandle.invoke(stashSupport)
}

class ClassicStashInstrumentationStash
object ClassicStashInstrumentationStash extends StashGetters {

  @OnMethodExit
  def onStashExit(@This stash: AnyRef): Unit = {
    ActorCellDecorator.get(getActorCell(stash)).foreach(_.stashSize.inc())
  }


}

class ClassicStashInstrumentationPrepend
object ClassicStashInstrumentationPrepend extends StashGetters {

  @OnMethodExit
  def onStashExit(@This stash: AnyRef, @Argument(0) seq: Seq[_]): Unit = {
    ActorCellDecorator.get(getActorCell(stash)).foreach(_.stashSize.add(seq.size))
  }


}


