package io.scalac.core

import io.scalac.core.model.RawLabels

trait LabelSerializer[T] extends (T => RawLabels) {
  final def apply(value: T): RawLabels = serialize(value)
  def serialize(value: T): RawLabels
}

object LabelSerializer {
  implicit def fromLabelSerializable[T <: LabelSerializable]: LabelSerializer[T] = _.serialize
}
