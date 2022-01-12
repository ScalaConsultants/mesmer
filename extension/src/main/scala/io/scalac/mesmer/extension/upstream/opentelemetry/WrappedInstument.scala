package io.scalac.mesmer.extension.upstream.opentelemetry

import io.scalac.mesmer.extension.metric.RegisterRoot

/**
 * Using this as intermediate object prevent from creating instruments that are never bound
 */
trait UnregisteredInstrument[T <: WrappedInstrument] extends (RegisterRoot => T) {
  def apply(root: RegisterRoot): T = register(root)
  def register(root: RegisterRoot): T
}

/**
 * Common base trait for both synchronous and asynchronous instruments
 */
trait WrappedInstrument
