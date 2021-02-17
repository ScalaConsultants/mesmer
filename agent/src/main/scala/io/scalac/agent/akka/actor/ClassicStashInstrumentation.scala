package io.scalac.agent.akka.actor

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import akka.actor.{ ActorContext, ActorRef }

class ClassicStashInstrumentation
object ClassicStashInstrumentation extends StashInstrumentation {

  protected val extractor: StashInstrumentation.Extractor = new StashInstrumentation.Extractor {
    private val lookup = MethodHandles.lookup()

    private val stashSupportClass = Class.forName("akka.actor.StashSupport")

    // Disclaimer:  The way we access the stash vector of and StashSupport is a quite ugly because it's an private field.
    //              We discovered its name during the debug and we aren't sure if this pattern is consistent through the compiler variations and versions.
    private val theStashMethodHandle =
      lookup.findVirtual(stashSupportClass, "akka$actor$StashSupport$$theStash", methodType(classOf[Vector[_]]))

    private val getSelfMethodHandle =
      lookup.findVirtual(stashSupportClass, "self", methodType(classOf[ActorRef]))

    private val getContextMethodHandle =
      lookup.findVirtual(stashSupportClass, "context", methodType(classOf[ActorContext]))

    def getStashSize(stashSupport: Any): Int =
      theStashMethodHandle.invoke(stashSupport).asInstanceOf[Vector[_]].length

    def getActorRef(stashSupport: Any): ActorRef =
      getSelfMethodHandle.invoke(stashSupport).asInstanceOf[ActorRef]

    def getContext(stashSupport: Any): ActorContext =
      getContextMethodHandle.invoke(stashSupport).asInstanceOf[ActorContext]

  }

}
