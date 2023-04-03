package io.scalac.mesmer.otelextension.instrumentations.akka.common

trait AbstractEvent extends Any { self =>
  type Service >: self.type
}
