package io.scalac.mesmer.core.util

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Field

object ReflectionFieldUtils {

  private val lookup = MethodHandles.publicLookup()

  @inline final def getHandlers(className: String, fieldName: String): (MethodHandle, MethodHandle) =
    getHandlers(Class.forName(className), fieldName)

  @inline final def getHandlers(clazz: Class[_], fieldName: String): (MethodHandle, MethodHandle) = {
    val field = getField(clazz, fieldName)
    (getGetter(field), getSetter(field))
  }

  @inline final def getGetter(className: String, fieldName: String): MethodHandle =
    getGetter(Class.forName(className), fieldName)

  @inline final def getGetter(clazz: Class[_], fieldName: String): MethodHandle =
    getGetter(getField(clazz, fieldName))

  @inline private final def getGetter(field: Field): MethodHandle =
    lookup.unreflectGetter(field)

  @inline final def getSetter(className: String, fieldName: String): MethodHandle =
    getGetter(Class.forName(className), fieldName)

  @inline final def getSetter(clazz: Class[_], fieldName: String): MethodHandle =
    getGetter(getField(clazz, fieldName))

  @inline private final def getSetter(field: Field): MethodHandle =
    lookup.unreflectSetter(field)

  @inline private final def getField(clazz: Class[_], fieldName: String): Field = {
    val field = clazz.getDeclaredField(fieldName)
    field.setAccessible(true)
    field
  }

}
