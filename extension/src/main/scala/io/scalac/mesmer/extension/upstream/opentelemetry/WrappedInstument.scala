package io.scalac.mesmer.extension.upstream.opentelemetry

import io.scalac.mesmer.extension.metric.RegisterRoot

/**
 * Using this as intermediate object prevent from creating instruments that are never bound
 */
trait UnregisteredInstrument[T <: WrappedInstrument] extends (RegisterRoot => T#Self) {
  def apply(root: RegisterRoot): T#Self = register(root)
  def register(root: RegisterRoot): T#Self
}

/**
 * Common base trait for both synchronous and asynchronous instruments
 */
trait WrappedInstrument { self =>
  type Self <: WrappedInstrument
}
