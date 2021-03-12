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

  @inline final private def get(container: Object): Option[AtomicLong] =
    Option(container).map(getter(container).invoke(_)).map(_.asInstanceOf[AtomicLong])
}

object CounterDecorator {

  class FixedClass(val fieldName: String, protected val decoratedClassName: String) extends CounterDecorator {
    private lazy val (getter, setter) = createHandlers(fieldName, decoratedClassName)

    @inline override final def getter(container: Object): MethodHandle = getter
    @inline override final def setter(container: Object): MethodHandle = setter
  }

  class Registry(val fieldName: String) extends CounterDecorator {
    private val cache = collection.concurrent.TrieMap.empty[String, Option[(MethodHandle, MethodHandle)]]

    final def register(className: String): Unit =
      cache(className) = None

    @inline override final def getter(container: Object): MethodHandle = entryFor(container)._1
    @inline override final def setter(container: Object): MethodHandle = entryFor(container)._2
    @inline private final def entryFor(container: Object): (MethodHandle, MethodHandle) = {
      val key = container.getClass.getName
      if (cache.contains(key)) {
        cache(key).getOrElse {
          val handlers = createHandlers(fieldName, key)
          cache(key) = Some(handlers)
          handlers
        }
      } else throw new RuntimeException(s"unregistered type $key")
    }
  }

  @inline private final def createHandlers(fieldName: String, className: String): (MethodHandle, MethodHandle) = {
    val field  = Class.forName(className).getDeclaredField(fieldName)
    val lookup = MethodHandles.publicLookup()
    field.setAccessible(true)
    (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
  }

}
