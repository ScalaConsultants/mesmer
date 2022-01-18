package io.scalac.mesmer.core

import io.scalac.mesmer.core.model.RawAttributes

trait AttributesSerializable {
  def serialize: RawAttributes
}
