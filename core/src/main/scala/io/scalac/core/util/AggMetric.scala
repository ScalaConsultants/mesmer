package io.scalac.core.util

import io.scalac.core.util.TimeSeries.LongTimeSeries

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
    def combine(timeSeries: LongTimeSeries): LongValueAggMetric = {
      val count = this.count + timeSeries.count
      val sum   = this.sum + timeSeries.sum
      val avg   = if (count == 0) 0L else Math.floorDiv(sum, count)
      LongValueAggMetric(
        min = if (this.min < timeSeries.min) this.min else timeSeries.min,
        max = if (this.max > timeSeries.min) this.max else timeSeries.max,
        avg = avg,
        sum = sum,
        count = count
      )
    }
  }

  final object LongValueAggMetric {
    def fromTimeSeries(ts: LongTimeSeries): LongValueAggMetric =
      LongValueAggMetric(ts.min, ts.max, ts.avg, ts.sum, ts.count)
  }

}
