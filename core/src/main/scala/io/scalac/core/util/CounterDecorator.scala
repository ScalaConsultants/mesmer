package io.scalac.core.util

import java.lang.invoke.MethodHandle
import java.util.concurrent.atomic.AtomicLong

sealed trait CounterDecorator {

  protected def getter(container: Object): MethodHandle
  protected def setter(container: Object): MethodHandle

  @inline final def initialize(container: Object, value: Long = 0): Unit =
    setter(container).invoke(container, new AtomicLong(value))

  @inline final def set(container: Object, value: Long): Unit =
    getRef(container)(_.set(value))

  @inline final def inc(container: Object): Unit =
    getRef(container)(_.getAndIncrement())

  @inline final def take(container: Object): Option[Long] =
    getRef(container)(_.getAndSet(0))

  @inline final def getValue(container: Object): Option[Long] =
    getRef(container)(_.get())

  // For a while, rest is for test propose
  @inline final def reset(container: Object): Unit =
    getRef(container)(_.set(0))

  @inline final private def getRef[@specialized(Long) T](container: Object)(onSuccess: AtomicLong => T): Option[T] =
    if (container ne null) {
      val atomic = getter(container).invoke(container)
      if (atomic ne null) {
        Some(onSuccess(atomic.asInstanceOf[AtomicLong]))
      } else None
    } else None
}

object CounterDecorator {

  class FixedClass(protected val decoratedClassName: String, val fieldName: String) extends CounterDecorator {
    private lazy val (fixedGetter, fixedSetter) = ReflectionFieldUtils.getHandlers(decoratedClassName, fieldName)

    @inline override final protected def getter(container: Object): MethodHandle = fixedGetter
    @inline override final protected def setter(container: Object): MethodHandle = fixedSetter
  }

}
