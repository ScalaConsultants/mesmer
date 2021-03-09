package io.scalac.core

package object tagging {

  trait Tagged[+U] extends Any { type Tag <: U }
  type @@[+T, +U] = T with Tagged[U]

  implicit class Tagger[T](val t: T) extends AnyVal {
    def taggedWith[U]: T @@ U = t.asInstanceOf[T @@ U]
  }

  implicit class TaggedOps[T, U](val tagged: @@[T, U]) extends AnyVal {
    def unwrap: T          = tagged.asInstanceOf[T]
    def retag[U2]: T @@ U2 = tagged.asInstanceOf[T @@ U2]
  }
}
