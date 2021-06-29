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

//  final class UninitializedCounter {
//
//    @volatile
//    private var counter: AtomicLong = _
//
//    def inc(): Unit            = ensureInitialized(_.getAndIncrement())
//    def add(value: Long): Unit = ensureInitialized(_.getAndAdd(value))
//    def take(): Option[Long]   = ifInitialized(_.getAndSet(0L))
//    def get(): Option[Long]    = ifInitialized(_.get())
//    def reset(): Unit          = ifInitialized(_.set(0L))
//    def set(value: Long): Unit = ensureInitialized(_.set(value))
//
//    def initialize(): Unit = counter = new AtomicLong(0L)
//
//    private def ifInitialized[@specialized(Long) T](map: AtomicLong => T): Option[T] =
//      if (counter ne null) Some(map(counter)) else None
//
//    private def ensureInitialized[@specialized(Long) T](map: AtomicLong => T): T = {
//      if (counter eq null) initialize()
//      map(counter)
//    }
//
//  }

}
