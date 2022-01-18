package io.scalac.mesmer.core

import io.scalac.mesmer.core.model.RawAttributes

trait AttributeSerializer[T] extends (T => RawAttributes) {
  final def apply(value: T): RawAttributes = serialize(value)
  def serialize(value: T): RawAttributes
}

object AttributeSerializer {
  implicit def fromAttributesSerializable[T <: AttributesSerializable]: AttributeSerializer[T] = _.serialize
}
