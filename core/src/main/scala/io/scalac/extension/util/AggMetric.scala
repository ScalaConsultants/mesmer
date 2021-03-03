package io.scalac.extension.util

import io.scalac.extension.util.TimeSeries.LongTimeSeries

sealed trait AggMetric[T, Avg] {
  def min: T
  def max: T
  def avg: Avg
}

object AggMetric {

  case class LongValueAggMetric(min: Long, max: Long, avg: Long, sum: Long) extends AggMetric[Long, Long]
  object LongValueAggMetric {
    def fromTimeSeries(ts: LongTimeSeries): LongValueAggMetric = LongValueAggMetric(ts.min, ts.max, ts.avg, ts.sum)
  }

}
