package io.scalac.mesmer.core.typeclasses

trait Encode[I, O] {
  def encode(input: I): O
}

object Encode {
  implicit class EncodeOps[I](private val input: I) extends AnyVal {
    def encode[O](implicit encode: Encode[I, O]): O = encode.encode(input)
  }
}
