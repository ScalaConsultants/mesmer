package io.scalac.core.util

import java.lang.invoke.{ MethodHandle, MethodHandles }

final object DecoratorUtils {

  @inline def createHandlers(className: String, fieldName: String): (MethodHandle, MethodHandle) = {
    val field  = Class.forName(className).getDeclaredField(fieldName)
    val lookup = MethodHandles.publicLookup()
    field.setAccessible(true)
    (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
  }

}
