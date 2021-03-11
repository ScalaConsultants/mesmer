package io.scalac.core.util

import java.lang.invoke.MethodHandles
import java.util.concurrent.atomic.AtomicLong

abstract class CounterDecorator(val fieldName: String, clazz: Class[_]) {
  import CounterDecorator._

  def this(fieldName: String, clazz: String) = this(fieldName, Class.forName(clazz))

  private lazy val (getter, setter) = {
    val field  = clazz.getDeclaredField(fieldName)
    val lookup = MethodHandles.publicLookup()
    field.setAccessible(true)
    (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
  }

  @inline final def initialize(actorCell: Object): Unit =
    setter.invoke(actorCell, new FieldType())

  @inline final def inc(actorCell: Object): Unit =
    get(actorCell).foreach(_.getAndIncrement())

  @inline final def take(actorCell: Object): Option[Long] =
    get(actorCell)
      .filter(_.get() > 0)
      .map(_.getAndSet(0))

  @inline private final def get(actorCell: Object): Option[FieldType] =
    Option(actorCell).map(getter.invoke(_)).map(_.asInstanceOf[FieldType])
}

object CounterDecorator {
  type FieldType = AtomicLong
}
