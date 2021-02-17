package io.scalac.agent.akka.actor

import java.lang.invoke.MethodType.methodType
import java.lang.invoke.MethodHandles

import akka.actor.{ ActorContext, ActorRef, ClassicActorContextProvider }

class TypeStashInstrumentation
object TypeStashInstrumentation extends StashInstrumentation {

  protected val extractor: StashInstrumentation.Extractor = new StashInstrumentation.Extractor {
    private val lookup = MethodHandles.lookup()

    private val stashBufferImplClass = Class.forName("akka.actor.typed.internal.StashBufferImpl")

    private val sizeMethodHandle =
      lookup.findVirtual(stashBufferImplClass, "size", methodType(classOf[Int]))

    // Disclaimer:  The way we access the context of and StashBufferImpl is a quite ugly because it's an private constructor param.
    //              We discovered its name during the debug and we aren't sure if this pattern is consistent through the compiler variations and versions.
    private val contextReflectively =
      stashBufferImplClass.getDeclaredField("akka$actor$typed$internal$StashBufferImpl$$ctx")

    private val getClassicContextMethodHandle =
      lookup.findVirtual(classOf[ClassicActorContextProvider], "classicActorContext", methodType(classOf[ActorContext]))

    def getStashSize(stash: Any): Int = sizeMethodHandle.invoke(stash).asInstanceOf[Int]

    def getActorRef(stash: Any): ActorRef = getContext(stash).self

    def getContext(stash: Any): ActorContext =
      getClassicContextMethodHandle.invoke(contextReflectively.get(stash)).asInstanceOf[ActorContext]
  }

}
