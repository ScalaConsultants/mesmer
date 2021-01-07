package io.scalac.core.util

class Timestamp(val value: Long) extends AnyVal {
  def diff(stop: Timestamp): Long = Timestamp.diff(this, stop)
}

object Timestamp {

  def create(): Timestamp = new Timestamp(System.nanoTime())

  def diff(start: Timestamp, stop: Timestamp): Long = math.floorDiv(math.abs(stop.value - start.value), 1_000_000)
}
