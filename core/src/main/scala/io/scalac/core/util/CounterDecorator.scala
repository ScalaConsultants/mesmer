package io.scalac.core.util

import java.lang.invoke.{ MethodHandle, MethodHandles }
import java.util.concurrent.atomic.AtomicLong

sealed trait CounterDecorator {

  protected def getter(container: Object): MethodHandle
  protected def setter(container: Object): MethodHandle

  @inline final def initialize(container: Object, value: Long = 0): Unit = set(container, value)

  @inline final def set(container: Object, value: Long): Unit =
    setter(container).invoke(container, new AtomicLong(value))

  @inline final def inc(container: Object): Unit =
    get(container).foreach(_.getAndIncrement())

  @inline def take(container: Object): Option[Long] =
    get(container).map(_.getAndSet(0))

  @inline final def getValue(container: Object): Option[Long] =
    get(container).map(_.get())

  // For a while, rest is for test propose
  @inline final def reset(container: Object): Unit =
    get(container).foreach(_.set(0))

  @inline final private def get(container: Object): Option[AtomicLong] =
    Option(container).map(getter(container).invoke(_)).map(_.asInstanceOf[AtomicLong])
}

object CounterDecorator {

  class FixedClass(protected val decoratedClassName: String, val fieldName: String) extends CounterDecorator {
    private lazy val (getter, setter) = DecoratorUtils.createHandlers(decoratedClassName, fieldName)

    @inline override final def getter(container: Object): MethodHandle = getter
    @inline override final def setter(container: Object): MethodHandle = setter
  }

}
