package io.scalac.mesmer.core.util

import io.scalac.mesmer.core.util.AggMetric.LongValueAggMetric.fromTimeSeries

sealed trait AggMetric[@specialized(Long) T] {
  def min: T
  def max: T
  def sum: T
  def count: Int
}

object AggMetric {

  /**
   * Case class with all aggregating values in milliseconds
   * @param min
   * @param max
   * @param avg
   * @param sum
   * @param count
   */
  final case class LongValueAggMetric(min: Long, max: Long, sum: Long, count: Int) extends AggMetric[Long] {

    def sum(timeSeries: TimeSeries[Long, Long]): LongValueAggMetric =
      sum(fromTimeSeries(timeSeries))

    /**
     * Sums all monotonically increasing values from this and other aggregation and
     * compute values for min and max
     * @param other
     * @return
     */
    def sum(other: LongValueAggMetric): LongValueAggMetric = {
      val count = this.count + other.count
      val sum   = this.sum + other.sum

      LongValueAggMetric(
        min = if (this.min < other.min) this.min else other.min,
        max = if (this.max > other.max) this.max else other.max,
        sum = sum,
        count = count
      )
    }

    /**
     * Adds this aggregation monotonically increasing counters to other
     * and leave it's min and max untouched
     * @param next aggregations which min and max will be preserved
     * @return
     */
    def addTo(next: LongValueAggMetric): LongValueAggMetric =
      next.copy(sum = next.sum + this.sum, count = next.count + this.count)
  }

  final object LongValueAggMetric {
    def fromTimeSeries(ts: TimeSeries[Long, Long]): LongValueAggMetric =
      LongValueAggMetric(ts.min, ts.max, ts.sum, ts.count)
  }

}
