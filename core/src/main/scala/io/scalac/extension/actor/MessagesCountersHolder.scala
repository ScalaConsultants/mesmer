package io.scalac.extension.actor

import java.lang.invoke.MethodHandles
import java.util.concurrent.atomic.AtomicLong

object MessagesCountersHolder {

  final object Received  extends CounterHolder("receivedMessages")
  final object Processed extends CounterHolder("processedMessages")
  final object Failed    extends CounterHolder("failedMessages")

  def setCounters(actorCell: Object): Unit = {
    Received.set(actorCell)
    Processed.set(actorCell)
    Failed.set(actorCell)
  }

  sealed abstract class CounterHolder(val fieldName: String) {
    private lazy val (getter, setter) = {
      val field  = Class.forName("akka.actor.ActorCell").getDeclaredField(fieldName)
      val lookup = MethodHandles.publicLookup()
      field.setAccessible(true)
      (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
    }

    @inline def set(actorCell: Object): Unit =
      setter.invoke(actorCell, new AtomicLong())

    @inline def inc(actorCell: Object): Unit =
      get(actorCell).foreach(_.getAndIncrement())

    @inline def take(actorCell: Object): Option[Long] =
      get(actorCell)
        .filter(_.get() > 0)
        .map(_.getAndSet(0))

    @inline private final def get(actorCell: Object): Option[AtomicLong] =
      Option(actorCell).map(getter.invoke(_)).map(_.asInstanceOf[AtomicLong])
  }
}
