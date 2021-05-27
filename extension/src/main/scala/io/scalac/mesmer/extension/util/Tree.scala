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

  def builder[K, V](implicit ordering: PartialOrdering[K]): Builder[K, V] = new Root[K, V](None, ArrayBuffer.empty)

  sealed trait Builder[K, V] {
    def insert(key: K, value: V): Builder[K, V]
    def remove(key: K): Builder[K, V]
    def build[O](filter: (K, V) => Option[O]): Option[Tree[O]]
  }

  trait TreeOrdering[T] {
    val partialOrdering: PartialOrdering[T]
    def isParent(x: T, y: T): Boolean      = partialOrdering.tryCompare(x, y).exists(_ < 0)
    def isChild(x: T, y: T): Boolean       = partialOrdering.tryCompare(x, y).exists(_ > 0)
    def isChildOrSame(x: T, y: T): Boolean = partialOrdering.tryCompare(x, y).exists(_ >= 0)
  }

  final class Root[K, V] private[util] (
    private[util] var root: Option[(K, V)],
    private[util] var children: ArrayBuffer[NonRoot[K, V]]
  )(implicit
    val partialOrdering: PartialOrdering[K]
  ) extends Builder[K, V]
      with Equals
      with TreeOrdering[K] {

    def insert(key: K, value: V): this.type = {
      root
        .fold[Unit] {
          if (children.isEmpty || parentOfAllChildren(key)) {
            root = Some(key, value)
          } else {
            children
              .find(nr => isChildOrSame(key, nr.key))
              .fold[Unit](children.append(NonRoot.leaf(key, value)))(_.insert(key, value))
          }
        } { case (rootKey, rootValue) =>
          partialOrdering
            .tryCompare(key, rootKey)
            .fold[Unit] {
              proxyChildren(rootKey, rootValue)
              children.append(NonRoot.leaf(key, value))
              root = None
            } { cmp =>
              if (cmp < 0) {
                proxyChildren(rootKey, rootValue)
                root = Some(key, value)
              } else if (cmp > 0) {
                children
                  .find(nr => isChildOrSame(key, nr.key))
                  .fold {
                    if (parentOfAllChildren(key)) {
                      proxyChildren(key, value)
                    } else {
                      children.append(NonRoot.leaf(key, value))
                    }
                  }(_.insert(key, value))
              }
            }

        }
      this
    }

    def insert(key: K)(implicit ev: K =:= V): this.type = insert(key, key)

    def remove(key: K): this.type = {

      root
        .filter(_._1 == key)
        .fold[Unit] {
          val index = children
            .indexWhere(nr => isChildOrSame(key, nr.key))
          if (index >= 0 && !children(index).remove(key)) {
            children.remove(index)
            normalize()
          }
        }(_ => root = None)
      this
    }

    def build[O](transform: (K, V) => Option[O]): Option[Tree[O]] =
      root.flatMap(Function.tupled(transform)).map { transformedRoot =>
        val inner = children.flatMap(_.build(transform)).toVector

        tree(transformedRoot, inner: _*)
      }

    def canEqual(that: Any): Boolean = that.isInstanceOf[Root[_, _]]

    override def equals(other: Any): Boolean = other match {
      case that: Root[_, _] =>
        that.canEqual(this) &&
          this.root == that.root &&
          this.children.size == that.children.size &&
          this.children.forall(that.children.contains)
      case _ => false
    }

    override def toString: String = s"Root(element: $root, children: ${children.mkString("[", ",", "]")})"

    private def proxyChildren(key: K, value: V): Unit = {
      val nr = new NonRoot[K, V](key, value, children)
      children = ArrayBuffer.empty
      children.append(nr)
    }

    private def parentOfAllChildren(key: K): Boolean = children.forall(nr => isParent(key, nr.key))

    private def normalize(): Unit =
      if (root.isEmpty && children.size == 1) {
        val onlyChild = children.head
        root = Some(onlyChild.key, onlyChild.value)
        children = onlyChild.children
      }
  }

  private[util] final class NonRoot[K, V](val key: K, val value: V, var children: ArrayBuffer[NonRoot[K, V]])(implicit
    val partialOrdering: PartialOrdering[K]
  ) extends Equals
      with TreeOrdering[K] {

    def insert(key: K, value: V): NonRoot[K, V] = {
      children
        .find(nr => isChildOrSame(key, nr.key))
        .fold[Unit] {
          if (value != this.value)
            children.append(NonRoot.leaf(key, value))
        }(_.insert(key, value))
      this
    }

    /**
     * @param key
     * @return true if it were able to remove the element. Note that true is returned even though element is not present in a tree
     *         false mean that element exists but we're not able to remove it from perspective from the current node and parent
     *         node must do it
     */
    def remove(key: K): Boolean =
      if (key == this.key) {
        false // we signal that whole object must be removed
      } else {
        val index = children
          .indexWhere(nr => isChildOrSame(key, nr.key))

        if (index >= 0 && !children(index).remove(key)) {
          children.remove(index)
        }
        true
      }

    def build[O](transform: (K, V) => Option[O]): Vector[Tree[O]] = {
      val nestedTree = children.flatMap(_.build(transform)).toVector

      transform(key, value).fold(nestedTree) { value =>
        Vector(tree(value, nestedTree: _*))
      }
    }

    def canEqual(that: Any): Boolean = that.isInstanceOf[NonRoot[_, _]]

    override def equals(other: Any): Boolean = other match {
      case that: NonRoot[_, _] =>
        that.canEqual(this) &&
          this.value == that.value &&
          this.children.size == that.children.size &&
          this.children.forall(that.children.contains)
      case _ => false
    }

    override def toString: String = s"NonRoot(key: $key, value: $value, children: ${children.mkString("[", ",", "]")}"
  }

  object NonRoot {

    private[util] def leaf[K, V](key: K, value: V)(implicit ordering: PartialOrdering[K]): NonRoot[K, V] =
      new NonRoot[K, V](key, value, ArrayBuffer.empty)

    private[util] def leaf[K](key: K)(implicit ordering: PartialOrdering[K]): NonRoot[K, K] =
      new NonRoot[K, K](key, key, ArrayBuffer.empty)

    private[util] def withChildren[K, V](key: K, value: V)(children: NonRoot[K, V]*)(implicit
      ordering: PartialOrdering[K]
    ): NonRoot[K, V] =
      new NonRoot[K, V](key, value, ArrayBuffer.from(children))

    private[util] def withChildren[K](key: K)(children: NonRoot[K, K]*)(implicit
      ordering: PartialOrdering[K]
    ): NonRoot[K, K] = withChildren[K, K](key, key)(children: _*)

    implicit def convertToLeaf[K](value: K)(implicit ordering: PartialOrdering[K]): NonRoot[K, K] = leaf(value, value)
  }

}
