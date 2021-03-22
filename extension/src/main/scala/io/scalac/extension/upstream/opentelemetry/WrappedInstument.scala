package io.scalac.extension.upstream.opentelemetry

import io.scalac.extension.metric.UnbindRoot

/**
 * Using this as intermediate object prevent from creating instruments that are never bound
 */
trait UnregisteredInstrument[T <: WrappedInstrument] {
  def register(root: UnbindRoot): T#Self
}

/**
 * Common base trait for both synchronous and asynchronous instruments
 */
trait WrappedInstrument { self =>
  type Self >: self.type <: WrappedInstrument
}
