package io.scalac.agent.akka.actor

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import akka.actor.ActorContext
import akka.actor.ActorRef
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

class ClassicStashInstrumentation
object ClassicStashInstrumentation {

  import Getters._

  @OnMethodExit
  def onStashExit(@This stash: Any): Unit =
    StashInstrumentation.publish(getStashSize(stash), getActorRef(stash), getContext(stash))

  private object Getters {

    private lazy val lookup = MethodHandles.lookup()

    private lazy val stashSupportClass = Class.forName("akka.actor.StashSupport")

    // Disclaimer:  The way we access the stash vector of and StashSupport is a quite ugly because it's an private field.
    //              We discovered its name during the debug and we aren't sure if this pattern is consistent through the compiler variations and versions.
    private lazy val theStashMethodHandle =
      lookup.findVirtual(stashSupportClass, "akka$actor$StashSupport$$theStash", methodType(classOf[Vector[_]]))

    private lazy val getSelfMethodHandle =
      lookup.findVirtual(stashSupportClass, "self", methodType(classOf[ActorRef]))

    private lazy val getContextMethodHandle =
      lookup.findVirtual(stashSupportClass, "context", methodType(classOf[ActorContext]))

    @inline final def getStashSize(stashSupport: Any): Int =
      theStashMethodHandle.invoke(stashSupport).asInstanceOf[Vector[_]].length

    @inline final def getActorRef(stashSupport: Any): ActorRef =
      getSelfMethodHandle.invoke(stashSupport).asInstanceOf[ActorRef]

    @inline final def getContext(stashSupport: Any): ActorContext =
      getContextMethodHandle.invoke(stashSupport).asInstanceOf[ActorContext]

  }

}
