package io.scalac.mesmer.core.util

sealed abstract class TimeSeries[@specialized(Long) T, @specialized(Long) Avg](data: Seq[T])(implicit n: Numeric[T]) {

  val min: T     = data.min
  val max: T     = data.max
  val sum: T     = data.sum
  val count: Int = data.size
  val avg: Avg   = div(sum, count)

  protected def div(v: T, n: Int): Avg

}

object TimeSeries {

  final class LongTimeSeries(data: Seq[Long]) extends TimeSeries[Long, Long](data) {
    protected def div(v: Long, n: Int): Long = if (n == 0) 0 else v / n
  }

  def apply(values: Long*): TimeSeries[Long, Long] = new LongTimeSeries(values)

}
