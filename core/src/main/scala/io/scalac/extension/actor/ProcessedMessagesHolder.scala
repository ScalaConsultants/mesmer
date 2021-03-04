package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

object ProcessedMessagesHolder {

  final val ProcessedMessagesVar = "processedMessages"

  private lazy val (processedMessagesGetterHandler, processedMessagesSetterHandler) = {
    val actorCellClass = Class.forName("akka.actor.ActorCell")
    val field          = actorCellClass.getDeclaredField(ProcessedMessagesVar)
    field.setAccessible(true)
    val lookup = MethodHandles.publicLookup()
    (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
  }

  @inline def inc(actorCell: Object): Unit =
    get(actorCell).foreach(current => processedMessagesSetterHandler.invoke(actorCell, current + 1))

  @inline def take(actorCell: Object): Option[Long] =
    get(actorCell)
      .filter(_ > 0)
      .map { current =>
        clear(actorCell)
        current
      }

  @inline private final def get(actorCell: Object): Option[Long] =
    Option(actorCell).map(processedMessagesGetterHandler.invoke(_)).map(_.asInstanceOf[Long])

  @inline private final def clear(actorCell: Object): Unit =
    processedMessagesSetterHandler.invoke(actorCell, 0)

}
