package io.scalac.agent.akka.actor

import java.lang.invoke.{ MethodHandles, MethodType }

import akka.actor.{ ActorContext, ActorRef }

class ClassicStashInstrumentation
object ClassicStashInstrumentation extends StashInstrumentation {

  protected val extractor: StashInstrumentation.Extractor = new StashInstrumentation.Extractor {
    private val lookup = MethodHandles.lookup()

    private val stashSupportClass = Class.forName("akka.actor.StashSupport")

    private val theStashMethodHandle = {
      val theStash = stashSupportClass.getDeclaredMethod("akka$actor$StashSupport$$theStash")
      theStash.setAccessible(true)
      lookup.unreflect(theStash)
    }

    private val getSelfMethodHandle =
      lookup.findVirtual(stashSupportClass, "self", MethodType.methodType(classOf[ActorRef]))

    private val getContextMethodHandle =
      lookup.findVirtual(stashSupportClass, "context", MethodType.methodType(classOf[ActorContext]))

    def getStashSize(stashSupport: Any): Int =
      theStashMethodHandle.invoke(stashSupport).asInstanceOf[Vector[_]].length

    def getActorRef(stashSupport: Any): ActorRef =
      getSelfMethodHandle.invoke(stashSupport).asInstanceOf[ActorRef]

    def getContext(stashSupport: Any): ActorContext =
      getContextMethodHandle.invoke(stashSupport).asInstanceOf[ActorContext]

  }

}
