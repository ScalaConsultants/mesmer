package io.scalac.extension.util

sealed abstract class TimeSeries[@specialized(Long) T, @specialized(Long) Avg](data: Seq[T])(implicit n: Numeric[T]) {

  def min: T     = data.min
  def max: T     = data.max
  def sum: T     = data.sum
  def count: Int = data.size
  def avg: Avg   = div(sum, count)

  protected def div(v: T, n: Int): Avg

}

object TimeSeries {

  final class LongTimeSeries(data: Seq[Long]) extends TimeSeries[Long, Long](data) {
    protected def div(v: Long, n: Int): Long = if (n == 0) 0 else v / n
  }

}
