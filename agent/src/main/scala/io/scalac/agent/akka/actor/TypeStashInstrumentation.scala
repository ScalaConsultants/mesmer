package io.scalac.agent.akka.actor

import java.lang.invoke.MethodType.methodType
import java.lang.invoke.MethodHandles

import akka.actor.{ ActorContext, ActorRef, ClassicActorContextProvider }

class TypeStashInstrumentation
object TypeStashInstrumentation extends StashInstrumentation {

  protected val extractor: StashInstrumentation.Extractor = new StashInstrumentation.Extractor {
    private val lookup = MethodHandles.lookup()

    private val stashBufferImplClass = Class.forName("akka.actor.typed.internal.StashBufferImpl")

    private val sizeReflectively =
      lookup.findVirtual(stashBufferImplClass, "size", methodType(classOf[Int]))

    private val contextReflectively =
      stashBufferImplClass.getDeclaredField("akka$actor$typed$internal$StashBufferImpl$$ctx")

    private val getClassicContextMethodHandle =
      lookup.findVirtual(classOf[ClassicActorContextProvider], "classicActorContext", methodType(classOf[ActorContext]))

    def getStashSize(stash: Any): Int = sizeReflectively.invoke(stash).asInstanceOf[Int]

    def getActorRef(stash: Any): ActorRef = getContext(stash).self

    def getContext(stash: Any): ActorContext =
      getClassicContextMethodHandle.invoke(contextReflectively.get(stash)).asInstanceOf[ActorContext]
  }

}
