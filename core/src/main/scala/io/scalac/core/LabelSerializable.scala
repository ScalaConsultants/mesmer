package io.scalac.core

import io.scalac.core.model.RawLabels

trait LabelSerializable {
  def serialize: RawLabels
}
