package io.scalac.agent.akka.actor

import akka.actor.{ Actor, ActorContext }
import io.scalac.extension.actor.ActorCountsDecorators
import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

class StashConstructorAdvice
object StashConstructorAdvice {

  @OnMethodExit
  def initStash(@This self: Actor): Unit =
    ActorCountsDecorators.Stash.initialize(self.context)

}

class ClassicStashInstrumentation
object ClassicStashInstrumentation {

  import Getters._

  @OnMethodExit
  def onStashExit(@This stash: AnyRef): Unit = {
    val size = getStashSize(stash)
    ActorCountsDecorators.Stash.set(getActorCell(stash), size)
  }

  private object Getters {

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

}
