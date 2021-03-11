package io.scalac.core.util

import java.lang.invoke.MethodHandles
import java.util.concurrent.atomic.AtomicLong

abstract class AtomicLongFieldDecorator(val fieldName: String, clazz: Class[_]) {
  def this(fieldName: String, clazz: String) = this(fieldName, Class.forName(clazz))

  private lazy val (getter, setter) = {
    val field  = clazz.getDeclaredField(fieldName)
    val lookup = MethodHandles.publicLookup()
    field.setAccessible(true)
    (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
  }

  @inline final def initialize(container: Object, value: Long = 0): Unit = set(container, value)

  @inline final def set(container: Object, value: Long): Unit =
    setter.invoke(container, new AtomicLong(value))

  @inline final def get(container: Object): Option[AtomicLong] =
    Option(container).map(getter.invoke(_)).map(_.asInstanceOf[AtomicLong])

}

abstract class CounterDecorator(fieldName: String, clazz: Class[_]) extends AtomicLongFieldDecorator(fieldName, clazz) {
  def this(fieldName: String, clazz: String) = this(fieldName, Class.forName(clazz))

  @inline final def inc(container: Object): Unit =
    get(container).foreach(_.getAndIncrement())

  @inline final def take(container: Object): Option[Long] =
    get(container)
      .filter(_.get() > 0)
      .map(_.getAndSet(0))

}
