package io.scalac.mesmer.core.typeclasses

trait Traverse[F[_]] {
  def sequence[T](obj: F[T]): Seq[T]
}
