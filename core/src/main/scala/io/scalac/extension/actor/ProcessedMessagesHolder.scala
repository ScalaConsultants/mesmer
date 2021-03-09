package io.scalac.extension.actor

import java.lang.invoke.MethodHandles
import java.util.concurrent.atomic.AtomicLong

object ProcessedMessagesHolder {

  final val ProcessedMessagesVar = "processedMessages"

  private lazy val (processedMessagesGetterHandler, processedMessagesSetterHandler) = {
    val actorCellClass = Class.forName("akka.actor.ActorCell")
    val field          = actorCellClass.getDeclaredField(ProcessedMessagesVar)
    field.setAccessible(true)
    val lookup = MethodHandles.publicLookup()
    (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
  }

  @inline def setCounter(actorCell: Object): Unit =
    processedMessagesSetterHandler.invoke(actorCell, new AtomicLong())

  @inline def inc(actorCell: Object): Unit =
    get(actorCell).foreach(_.getAndIncrement())

  @inline def take(actorCell: Object): Option[Long] =
    get(actorCell)
      .filter(_.get() > 0)
      .map(_.getAndSet(0))

  @inline private final def get(actorCell: Object): Option[AtomicLong] =
    Option(actorCell).map(processedMessagesGetterHandler.invoke(_)).map(_.asInstanceOf[AtomicLong])

}
