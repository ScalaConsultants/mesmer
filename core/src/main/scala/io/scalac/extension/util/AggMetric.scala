package io.scalac.extension.util

import io.scalac.extension.util.TimeSeries.LongTimeSeries

sealed trait AggMetric[@specialized(Long) T, @specialized(Long) Avg] {
  def min: T
  def max: T
  def sum: T
  def avg: Avg
  def count: Int
}

object AggMetric {

  final case class LongValueAggMetric(min: Long, max: Long, avg: Long, sum: Long, count: Int)
      extends AggMetric[Long, Long]
  final object LongValueAggMetric {
    def fromTimeSeries(ts: LongTimeSeries): LongValueAggMetric =
      LongValueAggMetric(ts.min, ts.max, ts.avg, ts.sum, ts.count)
  }

}
