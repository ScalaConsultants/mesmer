package io.scalac.mesmer.core.util

import io.scalac.mesmer.core.util.AggMetric.LongValueAggMetric.fromTimeSeries

sealed trait AggMetric[@specialized(Long) T, @specialized(Long) Avg] {
  def min: T
  def max: T
  def sum: T
  def avg: Avg
  def count: Int
}

object AggMetric {

  final case class LongValueAggMetric(min: Long, max: Long, avg: Long, sum: Long, count: Int)
      extends AggMetric[Long, Long] {
    def combine(timeSeries: TimeSeries[Long, Long]): LongValueAggMetric =
      combine(fromTimeSeries(timeSeries))

    def combine(other: LongValueAggMetric): LongValueAggMetric = {
      val count = this.count + other.count
      val sum   = this.sum + other.sum
      val avg   = if (count == 0) 0L else Math.floorDiv(sum, count)
      LongValueAggMetric(
        min = if (this.min < other.min) this.min else other.min,
        max = if (this.max > other.min) this.max else other.max,
        avg = avg,
        sum = sum,
        count = count
      )
    }
  }

  final object LongValueAggMetric {
    def fromTimeSeries(ts: TimeSeries[Long, Long]): LongValueAggMetric =
      LongValueAggMetric(ts.min, ts.max, ts.avg, ts.sum, ts.count)
  }

}
