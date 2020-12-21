package io.scalac.extension.metric

trait Synchronized {
  type Instrument[L]
  def atomically[A, B](first: Instrument[A], second: Instrument[B]): (A, B) => Unit
}
