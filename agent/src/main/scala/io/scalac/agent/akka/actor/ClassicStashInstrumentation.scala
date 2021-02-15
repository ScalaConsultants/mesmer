package io.scalac.agent.akka.actor

import akka.actor.{ ActorContext, ActorRef }
import akka.dispatch.Envelope

class ClassicStashInstrumentation
object ClassicStashInstrumentation extends StashInstrumentation {

  protected val utils: StashInstrumentation.Utils = new StashInstrumentation.Utils {

    private val stashSupportClass = Class.forName("akka.actor.StashSupport")

    private val getStashSizeReflectively = classOf[Vector[Envelope]].getMethod("length")

    private val theStashReflectively = {
      val theStash = stashSupportClass.getDeclaredMethod("akka$actor$StashSupport$$theStash")
      theStash.setAccessible(true)
      theStash
    }

    private val getSelfReflectively    = stashSupportClass.getDeclaredMethod("self")
    private val getContextReflectively = stashSupportClass.getDeclaredMethod("context")

    def getStashSize(stashSupport: Any): Int =
      getStashSizeReflectively.invoke(theStashReflectively.invoke(stashSupport)).asInstanceOf[Int]

    def getActorRef(stashSupport: Any): ActorRef = getSelfReflectively.invoke(stashSupport).asInstanceOf[ActorRef]

    def getContext(stashSupport: Any): ActorContext =
      getContextReflectively.invoke(stashSupport).asInstanceOf[ActorContext]

  }

}
