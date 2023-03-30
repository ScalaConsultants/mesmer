package io.scalac.mesmer.core.event

trait AbstractEvent extends Any { self =>
  type Service >: self.type
}
