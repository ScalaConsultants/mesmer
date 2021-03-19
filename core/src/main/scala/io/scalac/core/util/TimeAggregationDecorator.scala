package io.scalac.core.util

import scala.concurrent.duration.FiniteDuration

import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.LongNoLockAggregator

final class TimeAggregationDecorator {
  private val aggregator                  = new LongNoLockAggregator()
  def add(time: FiniteDuration): Unit     = aggregator.push(time.toMillis)
  def metrics: Option[LongValueAggMetric] = aggregator.fetch()
}
