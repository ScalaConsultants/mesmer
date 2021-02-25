package io.scalac.extension.metric

import scala.collection.mutable

import io.scalac.core.model.Entry
import io.scalac.core.util.Timestamp

sealed abstract class Distribution[T, Avg](data: Seq[Entry[T]])(implicit n: Numeric[T]) {
  private lazy val values = data.map(_.value)

  def min: T   = values.min
  def max: T   = values.max
  def sum: T   = values.sum
  def agv: Avg = div(sum, data.length)

  def hist: Seq[(Timestamp, Int)] = {
    val map = mutable.SortedMap.empty[Timestamp, Int]
    data.foreach(e => map(e.timestamp) = map.getOrElse(e.timestamp, 0) + 1)
    map.toSeq
  }

  protected def div(v: T, n: Int): Avg

}

object Distribution {

  final class LongDistribution(data: Seq[Entry[Long]]) extends Distribution[Long, Long](data) {
    protected def div(v: Long, n: Int): Long = if (n == 0) 0 else v / n
  }

}
