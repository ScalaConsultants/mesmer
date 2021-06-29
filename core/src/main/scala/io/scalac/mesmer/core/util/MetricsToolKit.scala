package io.scalac.mesmer.core.util

import io.scalac.mesmer.core.util.MinMaxSumCountAggregation.LongMinMaxSumCountAggregationImpl

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong, AtomicReference }

object MetricsToolKit {

  final class Counter {
    private val counter        = new AtomicLong(0)
    def inc(): Unit            = counter.getAndIncrement()
    def add(value: Long): Unit = counter.getAndAdd(value)
    def take(): Long           = counter.getAndSet(0)
    def get(): Long            = counter.get()
    def reset(): Unit          = counter.set(0)
  }

  final class Marker {
    private val marker           = new AtomicBoolean(false)
    def mark(): Unit             = marker.set(true)
    def checkAndReset(): Boolean = marker.getAndSet(false)
  }

  final class TimeAggregation {
    private[core] val aggregator                           = new LongNoLockAggregator()
    def add(time: Interval): Unit                          = aggregator.push(time)
    def metrics: Option[LongMinMaxSumCountAggregationImpl] = aggregator.fetch()
  }

  final class Timer {
    private val timestamp    = new AtomicReference[Timestamp]()
    def start(): Unit        = timestamp.set(Timestamp.create())
    def interval(): Interval = timestamp.get().interval()
  }

}
