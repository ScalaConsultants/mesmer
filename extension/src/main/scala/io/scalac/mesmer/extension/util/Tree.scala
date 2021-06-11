package io.scalac.mesmer.extension.util

import scala.annotation.tailrec
import scala.collection.mutable.{ ArrayBuffer, ListBuffer }

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

    def mapValues[E](func: T => E): Tree[E] =
      foldRight[Tree[E]] {
        case TreeF(value, Vector()) =>
          leaf(func(value))
        case TreeF(value, children) =>
          tree(func(value), children: _*)
      }

    def toVector: Vector[T] = foldRight[Vector[T]] { case TreeF(value, children) =>
      children.fold(Vector(value))(_ ++ _)
    }
  }

  def leaf[T](value: T): Tree[T] = tree(value)

  def tree[T](value: T, children: Tree[T]*): Tree[T] =
    Fix[({ type L[A] = TreeF[T, A] })#L](TreeF(value, children.toVector))

  def builder[K, V](implicit ordering: TreeOrdering[K]): Builder[K, V] = new Root[K, V](None, ArrayBuffer.empty)

  /**
   * Builder is a mutable structure
   * @param keyOrdering
   * @tparam K
   * @tparam V
   */
  sealed abstract class Builder[K, V](implicit val keyOrdering: TreeOrdering[K]) {

    /**
     * Inserts key and value into tree structure.
     *
     * @param key key to be inserted
     * @param value value to be inserted
     * @return new builder with specified element
     */
    def insert(key: K, value: V): Builder[K, V]

    /**
     * Removes single element from tree under key preserving it's children.
     *
     * @param key
     * @return Removed element and new builder
     */
    def remove(key: K): (Option[V], Builder[K, V])

    /**
     * Removes all elements that are logically after this key (inclusively).
     * All elements after are removed even if key is not present.
     *
     * @param key key after which all elements should be removed (the key included)
     * @return new builder without elements after the key
     */
    def removeAfter(key: K): (Vector[V], Builder[K, V])

    /**
     * Mapping for values contained under specific key.
     *
     * @param key key under which modification should be performed
     * @param func mapping function
     * @return new builder containing mapped value
     */
    def modify(key: K, func: V => V): Builder[K, V]

    /**
     * Creates immutable tree from current builder using filter function. If builder
     * doesn't have a root value (this might happen under specific circumstances), returns None.
     *
     * @param filter filter function that allow to transform / filter out elements
     * @tparam O type of elements in immutable tree
     * @return new immutable tree
     */
    def buildTree[O](filter: (K, V) => Option[O]): Option[Tree[O]]

    /**
     * Creates flat structure from all elements in current builder.
     *
     * @param filter filter function that allow to transform / filter out elements
     * @tparam O type of elements in immutable tree
     * @return new flat structure
     */
    def buildSeq[O](filter: (K, V) => Option[O]): Seq[O]



  }

  trait TreeOrdering[T] {
    def isParent(x: T, y: T): Boolean
    def isChild(x: T, y: T): Boolean
    def isChildOrSame(x: T, y: T): Boolean
    def isParentOrSame(x: T, y: T): Boolean
    def isParentIfSameBranch(x: T, y: T): Option[Boolean]
  }

  object TreeOrdering {
    implicit def fromPartialOrdering[T](implicit partialOrdering: PartialOrdering[T]): TreeOrdering[T] = new TreeOrdering[T] {
      def isParent(x: T, y: T): Boolean                     = partialOrdering.tryCompare(x, y).exists(_ < 0)
      def isChild(x: T, y: T): Boolean                      = partialOrdering.tryCompare(x, y).exists(_ > 0)
      def isChildOrSame(x: T, y: T): Boolean                = partialOrdering.tryCompare(x, y).exists(_ >= 0)
      def isParentIfSameBranch(x: T, y: T): Option[Boolean] = partialOrdering.tryCompare(x, y).map(_ < 0)
      def isParentOrSame(x: T, y: T): Boolean               = partialOrdering.tryCompare(x, y).exists(_ <= 0)
    }
  }

  final class Root[K, V] private[util] (
    private[util] var root: Option[(K, V)],
    private[util] var children: ArrayBuffer[NonRoot[K, V]]
  )(implicit ordering: TreeOrdering[K])
      extends Builder[K, V]
      with Equals {

    import keyOrdering._

    private def insertToChildren(key: K, value: V): this.type =
      this

    /**
     * Proxies all children that has parent relation with key
     * @param key key to proxy children with
     * @param value value to proxy children with
     * @return true if any child was proxied otherwise false
     */
    private def proxyOrInsertChildren(key: K, value: V): Unit = {
      val selectedChildren = children
        .filter(node => isParent(key, node.key))

      if (selectedChildren.nonEmpty) {
        children.subtractAll(selectedChildren)
        children.append(new NonRoot[K, V](key, value, selectedChildren))
      } else children.append(NonRoot.leaf(key, value))

    }

    private def insertWithChildren(key: K, value: V): Unit =
      children
        .find(nr => isChildOrSame(key, nr.key))
        .fold[Unit] {
          proxyOrInsertChildren(key, value)
        }(_.insert(key, value))

    def insert(key: K, value: V): this.type = {
      root
        .fold[Unit] {
          if (children.isEmpty || parentOfAllChildren(key)) {
            root = Some(key, value)
          } else {
            insertWithChildren(key, value)
          }
        } { case (rootKey, rootValue) =>
          isParentIfSameBranch(key, rootKey)
            .fold[Unit] {
              proxyAllChildren(rootKey, rootValue)
              children.append(NonRoot.leaf(key, value))
              root = None
            } { isParent =>
              if (isParent) {
                proxyAllChildren(rootKey, rootValue)
                root = Some(key, value)
              } else if (rootKey == key) {
                root = Some(key, value)
              } else {
                insertWithChildren(key, value)

              }
            }

        }
      this
    }

    def insert(key: K)(implicit ev: K =:= V): this.type = insert(key, key)

    def remove(key: K): (Option[V], this.type) =
      root
        .filter(_._1 == key)
        .fold[(Option[V], this.type)] {
          val index = children
            .indexWhere(nr => isChildOrSame(key, nr.key))

          if (index >= 0) {
            val selectedChild = children(index)

            if (selectedChild.key == key) {
              children.remove(index)
              children.appendAll(selectedChild.children)
              normalize()
              (Some(selectedChild.value), this)
            } else {
              (selectedChild.remove(key), this)
            }
          } else (None, this)

        } { case (_, rootValue) =>
          root = None
          normalize()
          (Some(rootValue), this)
        }

    /**
     * Removes all elements that are logically after this key (inclusively).
     * All elements after are removed even if key is not present.
     *
     * @param key key after which all elements should be removed (the key included)
     * @return new builder without elements after the key
     */
    def removeAfter(key: K): (Vector[V], Builder[K, V]) = {
      val nodesRemoved = root.filter { case (rootKey, _) =>
        isParentOrSame(key, rootKey)
      }.fold[Vector[V]] {

        val index = children
          .indexWhere(nr => isChildOrSame(key, nr.key))

        if (index >= 0) {
          val selectedChild = children(index)

          if (selectedChild.key == key) {
            children.remove(index)
            NonRoot.allValues(selectedChild)
          } else {
            selectedChild.removeAfter(key)
          }

        } else {
          val selectedChildren = children.filter(nr => isParent(key, nr.key))
          children.subtractAll(selectedChildren)
          NonRoot.allValues(selectedChildren.toSeq: _*)
        }

      } { case (_, rootValue) =>
        root = None
        val childrenValues = NonRoot.allValues(children.toSeq: _*)
        children.clear()
        rootValue +: childrenValues
      }

      (nodesRemoved, this)
    }

    def modify(key: K, func: V => V): this.type = {
      root
        .filter(_._1 == key)
        .fold[Unit] {
          val index = children
            .indexWhere(nr => isChildOrSame(key, nr.key))

          if (index >= 0 && !children(index).modify(key, func)) {
            val child = children(index)
            children.update(index, new NonRoot[K, V](child.key, func(child.value), child.children))
          }
        } { case (key, value) =>
          root = Some(key, func(value))
        }
      this
    }

    def buildTree[O](transform: (K, V) => Option[O]): Option[Tree[O]] =
      root.flatMap(Function.tupled(transform)).map { transformedRoot =>
        val inner = children.flatMap(_.buildTree(transform)).toVector

        tree(transformedRoot, inner: _*)
      }

    def buildSeq[O](transform: (K, V) => Option[O]): Seq[O] =
      root.flatMap(Function.tupled(transform)).toSeq ++ children.flatMap(_.buildSeq(transform)).toSeq

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

    private def proxyAllChildren(key: K, value: V): Unit = {
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
    val ordering: TreeOrdering[K]
  ) extends Equals {

    import ordering._

    /**
     * Proxies all children that has parent relation with key
     * @param key key to proxy children with
     * @param value value to proxy children with
     * @return true if any child was proxied otherwise false
     */
    private def proxyOrInsertChildren(key: K, value: V): Unit = {
      val selectedChildren = children
        .filter(node => isParent(key, node.key))

      if (selectedChildren.nonEmpty) {
        children.subtractAll(selectedChildren)
        children.append(new NonRoot[K, V](key, value, selectedChildren))
      } else children.append(NonRoot.leaf(key, value))

    }

    private def insertWithChildren(key: K, value: V): Unit =
      children
        .find(nr => isChildOrSame(key, nr.key))
        .fold[Unit] {
          proxyOrInsertChildren(key, value)
        }(_.insert(key, value))

    def insert(key: K, value: V): NonRoot[K, V] = {
      children
        .find(nr => isChildOrSame(key, nr.key))
        .fold[Unit] {
          if (key != this.key) {
            insertWithChildren(key, value)
          }
        }(_.insert(key, value))
      this
    }

    /**
     * @param key to be removed from children
     * @return Some(value) if key is contained in children nodes otherwise None
     */
    def remove(key: K): Option[V] = {

      val index = children
        .indexWhere(nr => isChildOrSame(key, nr.key))

      if (index >= 0) {
        val selectedChild = children(index)
        if (selectedChild.key == key) {
          children.remove(index)
          children.appendAll(selectedChild.children)
          Some(selectedChild.value)
        } else {
          selectedChild.remove(key)
        }
      } else {
        None
      }
    }

    def removeAfter(key: K): Vector[V] = {

      val index = children
        .indexWhere(nr => isChildOrSame(key, nr.key))

      if (index >= 0) {
        val selectedChild = children(index)

        if (selectedChild.key == key) {
          children.remove(index)
          selectedChild.value +: NonRoot.allValues(selectedChild)
        } else {
          selectedChild.removeAfter(key)
        }

      } else {
        val selectedChildren = children.filter(nr => isParent(key, nr.key))
        children.subtractAll(selectedChildren)
        NonRoot.allValues(selectedChildren.toSeq: _*)
      }
    }

    def modify(key: K, func: V => V): Boolean =
      if (key == this.key) {
        false // we signal that whole object must be removed
      } else {
        val index = children
          .indexWhere(nr => isChildOrSame(key, nr.key))
        if (index >= 0) {
          val child = children(index)
          if (!children(index).modify(key, func)) {
            children(index) = new NonRoot[K, V](child.key, func(child.value), child.children)
          }
        }
        true
      }

    def buildSeq[O](transform: (K, V) => Option[O]): Seq[O] =
      transform(key, value).toSeq ++ children.flatMap(_.buildSeq(transform)).toSeq

    def buildTree[O](transform: (K, V) => Option[O]): Vector[Tree[O]] = {
      val nestedTree = children.flatMap(_.buildTree(transform)).toVector

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

    private[util] def leaf[K, V](key: K, value: V)(implicit ordering: TreeOrdering[K]): NonRoot[K, V] =
      new NonRoot[K, V](key, value, ArrayBuffer.empty)

    private[util] def leaf[K](key: K)(implicit ordering: TreeOrdering[K]): NonRoot[K, K] =
      new NonRoot[K, K](key, key, ArrayBuffer.empty)

    private[util] def withChildren[K, V](key: K, value: V)(children: NonRoot[K, V]*)(implicit
      ordering: TreeOrdering[K]
    ): NonRoot[K, V] =
      new NonRoot[K, V](key, value, ArrayBuffer.from(children))

    private[util] def withChildren[K](key: K)(children: NonRoot[K, K]*)(implicit
      ordering: TreeOrdering[K]
    ): NonRoot[K, K] = withChildren[K, K](key, key)(children: _*)

    private[util] def allValues[K, V](nodes: NonRoot[K, V]*): Vector[V] = {

      @tailrec
      def loop(acc: ListBuffer[V], left: List[NonRoot[K, V]]): Vector[V] =
        left match {
          case Nil => acc.toVector
          case x :: xs =>
            acc.prepend(x.value)
            loop(acc, xs.prependedAll(x.children))
        }

      loop(ListBuffer.empty, nodes.toList)
    }

    implicit def convertToLeaf[K](value: K)(implicit ordering: TreeOrdering[K]): NonRoot[K, K] = leaf(value, value)
  }

}
