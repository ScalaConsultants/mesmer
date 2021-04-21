package io.scalac.mesmer.core

import io.scalac.mesmer.core.model.RawLabels

trait LabelSerializable {
  def serialize: RawLabels
}
