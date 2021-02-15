package io.scalac.agent.akka.actor

import akka.actor.ClassicActorContextProvider
import akka.{ actor => classic }

class TypeStashInstrumentation
object TypeStashInstrumentation extends StashInstrumentation {

  protected val utils: StashInstrumentation.Utils = new StashInstrumentation.Utils {
    private val stashBufferImplClass = Class.forName("akka.actor.typed.internal.StashBufferImpl")

    private val sizeReflectively = stashBufferImplClass.getDeclaredMethod("size")

    private val contextReflectively =
      stashBufferImplClass.getDeclaredField("akka$actor$typed$internal$StashBufferImpl$$ctx")

    private val getClassicContext = {
      val classicActorContext = classOf[ClassicActorContextProvider].getDeclaredMethod("classicActorContext")
      classicActorContext.setAccessible(true)
      classicActorContext
    }

    def getStashSize(stash: Any): Int = sizeReflectively.invoke(stash).asInstanceOf[Int]

    def getActorRef(stash: Any): classic.ActorRef = getContext(stash).self

    def getContext(stash: Any): classic.ActorContext =
      getClassicContext.invoke(contextReflectively.get(stash)).asInstanceOf[classic.ActorContext]
  }

}
