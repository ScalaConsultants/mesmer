package io.scalac.mesmer.extension.util

import scala.collection.mutable.ArrayBuffer

final case class TreeF[+T, F](value: T, inner: Vector[F]) {
  def map[B](func: F => B): TreeF[T, B] = TreeF(value, inner.map(func))
}

final case class Fix[F[_]](unfix: F[Fix[F]]) extends AnyVal

object Tree {

  type Tree[T] = Fix[({ type L[A] = TreeF[T, A] })#L]

  implicit final class TreeFOps[T](private val value: TreeF[T, Fix[({ type L[A] = TreeF[T, A] })#L]]) extends AnyVal {
    //non recursive tree
    private type NRTree[A] = TreeF[T, A]

    // cata
    private def innerFoldRight[A](alg: NRTree[A] => A)(fix: Fix[NRTree]): A =
      alg(fix.unfix.map(innerFoldRight(alg)))

    def foldRight[A](alg: NRTree[A] => A): A = alg(value.map(innerFoldRight(alg)))
  }

  def leaf[T](value: T): Tree[T] = tree(value)

  def tree[T](value: T, children: Tree[T]*): Tree[T] =
    Fix[({ type L[A] = TreeF[T, A] })#L](TreeF(value, children.toVector))

  def builder[T](implicit ordering: PartialOrdering[T]): Builder[T] = new Root[T](None, ArrayBuffer.empty)

  sealed trait Builder[T] {
    def insert(elem: T): Builder[T]
    def remove(value: T): Builder[T]
    def build[V](filter: T => Option[V]): Option[Tree[V]]
  }

  trait TreeOrdering[T] {
    val partialOrdering: PartialOrdering[T]
    def isParent(x: T, y: T): Boolean      = partialOrdering.tryCompare(x, y).exists(_ < 0)
    def isChild(x: T, y: T): Boolean       = partialOrdering.tryCompare(x, y).exists(_ > 0)
    def isChildOrSame(x: T, y: T): Boolean = partialOrdering.tryCompare(x, y).exists(_ >= 0)
  }

  final class Root[T] private[util] (
    private[util] var root: Option[T],
    private[util] var children: ArrayBuffer[NonRoot[T]]
  )(implicit
    val partialOrdering: PartialOrdering[T]
  ) extends Builder[T]
      with Equals
      with TreeOrdering[T] {

    def insert(element: T): this.type = {
      root
        .fold[Unit] {
          if (children.isEmpty || parentOfAllChildren(element)) {
            root = Some(element)
          } else {
            children
              .find(nr => isChildOrSame(element, nr.element))
              .fold[Unit](children.append(NonRoot.leaf(element)))(_.insert(element))
          }
        } { rootElement =>
          partialOrdering
            .tryCompare(element, rootElement)
            .fold[Unit] {
              proxyChildren(rootElement)
              children.append(NonRoot.leaf(element))
              root = None
            } { cmp =>
              if (cmp < 0) {
                proxyChildren(rootElement)
                root = Some(element)
              } else if (cmp > 0) {
                children
                  .find(nr => isChildOrSame(element, nr.element))
                  .fold {
                    if (parentOfAllChildren(element)) {
                      proxyChildren(element)
                    } else {
                      children.append(NonRoot.leaf(element))
                    }
                  }(_.insert(element))
              }
            }

        }
      this
    }

    def remove(value: T): this.type = {

      root
        .filter(_ == value)
        .fold[Unit] {
          val index = children
            .indexWhere(nr => isChildOrSame(value, nr.element))
          if (index >= 0 && !children(index).remove(value)) {
            children.remove(index)
            normalize()
          }
        }(_ => root = None)
      this
    }

    def build[V](transform: T => Option[V]): Option[Tree[V]] =
      root.flatMap(transform).map { rootElement =>
        val inner = children.flatMap(_.build(transform)).toVector

        tree(rootElement, inner: _*)
      }

    def canEqual(that: Any): Boolean = that.isInstanceOf[Root[_]]

    override def equals(other: Any): Boolean = other match {
      case that: Root[_] =>
        that.canEqual(this) &&
          this.root == that.root &&
          this.children.size == that.children.size &&
          this.children.forall(that.children.contains)
      case _ => false
    }

    override def toString: String = s"Root(element: $root, children: ${children.mkString("[", ",", "]")})"

    private def proxyChildren(rt: T): Unit = {
      val nr = new NonRoot[T](rt, children)
      children = ArrayBuffer.empty
      children.append(nr)
    }

    private def parentOfAllChildren(element: T): Boolean = children.forall(nr => isParent(element, nr.element))

    private def normalize(): Unit =
      if (root.isEmpty && children.size == 1) {
        val onlyChild = children.head
        root = Some(onlyChild.element)
        children = onlyChild.children
      }
  }

  private[util] final class NonRoot[T](var element: T, var children: ArrayBuffer[NonRoot[T]])(implicit
    val partialOrdering: PartialOrdering[T]
  ) extends Equals
      with TreeOrdering[T] {

    def insert(value: T): NonRoot[T] = {
      children
        .find(nr => isChildOrSame(value, nr.element))
        .fold[Unit] {
          if (element != value)
            children.append(NonRoot.leaf(value))
        }(_.insert(value))
      this
    }

    def remove(value: T): Boolean =
      if (element == value) {
        false // we signal that whole object must be removed
      } else {
        val index = children
          .indexWhere(nr => isChildOrSame(value, nr.element))
        if (index >= 0 && !children(index).remove(value)) {
          children.remove(index)
        }
        true
      }

    def build[V](transform: T => Option[V]): Vector[Tree[V]] = {
      val nestedTree = children.flatMap(_.build(transform)).toVector

      transform(element).fold(nestedTree) { value =>
        Vector(tree(value, nestedTree: _*))
      }
    }

    def canEqual(that: Any): Boolean = that.isInstanceOf[NonRoot[_]]

    override def equals(other: Any): Boolean = other match {
      case that: NonRoot[_] =>
        that.canEqual(this) &&
          this.element == that.element &&
          this.children.size == that.children.size &&
          this.children.forall(that.children.contains)
      case _ => false
    }

    override def toString: String = s"NonRoot(element: $element, children: ${children.mkString("[", ",", "]")}"
  }

  object NonRoot {

    private[util] def leaf[T](element: T)(implicit ordering: PartialOrdering[T]): NonRoot[T] =
      new NonRoot[T](element, ArrayBuffer.empty)

    private[util] def withChildren[T](element: T)(children: NonRoot[T]*)(implicit
      ordering: PartialOrdering[T]
    ): NonRoot[T] =
      new NonRoot[T](element, ArrayBuffer.from(children))

    implicit def convertToLeaf[T](value: T)(implicit ordering: PartialOrdering[T]): NonRoot[T] = leaf(value)
  }

}
