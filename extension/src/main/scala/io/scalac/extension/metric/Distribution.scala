package io.scalac.extension.metric

import scala.collection.mutable

import io.scalac.core.util.Timestamp

import Distribution.Entry

sealed abstract class Distribution[T, Avg](data: Seq[Entry[T]])(implicit n: Numeric[T]) {
  private lazy val values = data.map(_.value)

  def minValue: T   = values.min
  def maxValue: T   = values.max
  def sumValue: T   = values.sum
  def agvValue: Avg = div(sumValue, data.length)

  def hist: Seq[(Timestamp, Int)] = {
    val map = mutable.SortedMap.empty[Timestamp, Int]
    data.foreach(e => map(e.timestamp) = map.getOrElse(e.timestamp, 0) + 1)
    map.toSeq
  }

  protected def div(v: T, n: Int): Avg

}

object Distribution {

  final case class Entry[T](value: T, timestamp: Timestamp)

  final class LongDistribution(data: Seq[Entry[Long]]) extends Distribution[Long, Long](data) {
    protected def div(v: Long, n: Int): Long = if (n == 0) 0 else v / n
  }

}
