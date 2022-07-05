package io.scalac.mesmer.core.typeclasses

trait Encode[I, O] {
  def encode(input: I): O
}
