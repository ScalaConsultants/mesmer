package io.scalac.mesmer.extension.util

final case class TreeF[+T, F](value: T, inner: Vector[F]) {
  def map[B](func: F => B): TreeF[T, B] = TreeF(value, inner.map(func))
}

final case class Fix[F[_]](unfix: F[Fix[F]]) extends AnyVal

object Tree extends App {

//  type Tree[T] = Fix

  type NestedTree[T] = Fix[({ type L[A] = TreeF[T, A] })#L]

  implicit final class TreeFOps[T](private val value: TreeF[T, Fix[({ type L[A] = TreeF[T, A] })#L]]) extends AnyVal {
    private type Tree[A] = TreeF[T, A]
    // cata
    private def innerFoldRight[A](alg: Tree[A] => A)(fix: Fix[Tree]): A =
      alg(fix.unfix.map(innerFoldRight(alg)))

    def foldRight[A](alg: Tree[A] => A): A = alg(value.map(innerFoldRight(alg)))
  }

  def leaf[T](value: T): NestedTree[T] = tree(value)

  def tree[T](value: T, children: NestedTree[T]*): NestedTree[T] =
    Fix[({ type L[A] = TreeF[T, A] })#L](TreeF(value, children.toVector))
}
