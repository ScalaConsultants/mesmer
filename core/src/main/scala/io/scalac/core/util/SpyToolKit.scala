package io.scalac.core.util

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong, AtomicReference }

import scala.concurrent.duration._

import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.LongNoLockAggregator

object SpyToolKit {

  final class Counter {
    private val counter = new AtomicLong(0)
    def inc(): Unit     = counter.getAndIncrement()
    def take(): Long    = counter.getAndSet(0)
    def get(): Long     = counter.get()
    def reset(): Unit   = counter.set(0)
  }

  final class Marker {
    private val marker           = new AtomicBoolean(false)
    def mark(): Unit             = marker.set(true)
    def checkAndReset(): Boolean = marker.getAndSet(false)
  }

  final class TimeAggregation {
    private val aggregator                  = new LongNoLockAggregator()
    def add(time: FiniteDuration): Unit     = aggregator.push(time.toMillis)
    def metrics: Option[LongValueAggMetric] = aggregator.fetch()
  }

  final class Timer {
    private val timestamp          = new AtomicReference[Timestamp]()
    def start(): Unit              = timestamp.set(Timestamp.create())
    def interval(): FiniteDuration = timestamp.get().interval().milliseconds
  }

}
