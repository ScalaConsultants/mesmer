package io.scalac.mesmer.extension.service

import scala.annotation.tailrec
import scala.math.PartialOrdering

final case class NonEmptyTree[+T] private (value: T, children: Seq[NonEmptyTree[T]]) {
  final def insert[B >: T](element: B)(implicit ordering: PartialOrdering[B]): NonEmptyTree[B] =
    ordering.tryCompare(element, value) match {
      case Some(x) if x < 0 => NonEmptyTree.withChildren(element)(this)
      case Some(x) if x > 0 =>
        val updateIndex = children.indexWhere(subtree => ordering.tryCompare(element, subtree.value).exists(_ > 0))
        if (updateIndex > -1) {
          this.copy(children = children.updated(updateIndex, children(updateIndex).insert(element)))
        } else this.copy(children = children :+ NonEmptyTree(element))

      case _ => this // we are not connected by any edge
    }

  //stack-safe fold
  def foldRight[B](init: B)(merge: (T, Seq[B]) => B): B = {
    @tailrec
    def loop(stack: List[Seq[NonEmptyTree[T]]], terminal: List[Int], results: List[B]): B =
      stack match {
        case _ :: Nil if results.nonEmpty => results.head // value left on stack is the solution
        case head :: tail =>
          val index = terminal.head

          if (index != head.size) { // not finished calculating layer
            val current = head(index)

            if (current.children.isEmpty) { // can transform this value eg Leaf case
              loop(stack, index + 1 :: terminal.tail, merge(current.value, Seq(init)) :: results)
            } else {
              // unpack children on the stack
              loop(current.children :: stack, 0 :: terminal, results)
            }
          } else { // finished all children
            val (currentLayerResults, leftResults) = results.splitAt(index)
            val ancestorsTerminals                 = terminal.tail
            val parent                             = tail.head(ancestorsTerminals.head)
            val mergedValue                        = merge(parent.value, currentLayerResults.reverse)

            loop(
              tail,
              ancestorsTerminals.head + 1 :: ancestorsTerminals.tail,
              mergedValue :: leftResults
            )
          }
      }

    loop(List(Seq(this)), List(0), Nil)
  }
}

object NonEmptyTree {
  def apply[T](value: T): NonEmptyTree[T]                                    = NonEmptyTree(value, Seq.empty)
  def withChildren[T](value: T)(children: NonEmptyTree[T]*): NonEmptyTree[T] = NonEmptyTree(value, children.toSeq)

  def fromSeq[T](elements: Seq[T])(implicit ordering: PartialOrdering[T]): Option[NonEmptyTree[T]] =
    elements
      .sortWith(ordering.lt) match {
      case Seq(head, tail @ _*) => Some(tail.foldLeft(NonEmptyTree(head))(_.insert(_)))
      case _                    => None
    }

}
