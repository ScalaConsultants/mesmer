package io.scalac.mesmer.core.util

import _root_.io.scalac.mesmer.core.invoke.Lookup
import akka.MesmerMirrorTypes.ActorSystemImpl
import akka.actor.ActorSystem
import akka.util.Unsafe

object ClassicActorSystemOps extends Lookup {

  private val initializedOffset: Long =
    Unsafe.instance.objectFieldOffset(classOf[ActorSystemImpl].getDeclaredField("_initialized"))

  implicit final class ActorSystemOps(private val system: ActorSystem) extends AnyVal {

    /**
     * Unsafe is used to ensure volatile semantics on field access
     * @return
     */
    def isInitialized: Boolean =
      Unsafe.instance.getBooleanVolatile(system, initializedOffset)

  }
}
