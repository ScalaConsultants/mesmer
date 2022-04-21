package io.scalac.mesmer.core.typeclasses

trait Combine[T] {
  def combine(first: T, second: T): T
}
