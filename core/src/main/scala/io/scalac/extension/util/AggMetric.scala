package io.scalac.extension.util

import io.scalac.extension.util.TimeSeries.LongTimeSeries

sealed trait AggMetric[@specialized T, @specialized Avg] {
  def min: T
  def max: T
  def sum: T
  def avg: Avg
  def count: Int
}

object AggMetric {

  case class LongValueAggMetric(min: Long, max: Long, avg: Long, sum: Long, count: Int) extends AggMetric[Long, Long]
  object LongValueAggMetric {
    def fromTimeSeries(ts: LongTimeSeries): LongValueAggMetric =
      LongValueAggMetric(ts.min, ts.max, ts.avg, ts.sum, ts.count)
  }

}
