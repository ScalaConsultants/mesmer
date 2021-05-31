package io.scalac.mesmer.core.util

import io.scalac.mesmer.core.util.AggMetric.LongValueAggMetric

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong, AtomicReference }
import scala.concurrent.duration._

object MetricsToolKit {

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
    private[core] val aggregator                  = new LongNoLockAggregator()
    def add(time: FiniteDuration): Unit     = aggregator.push(time.toNanos)
    def metrics: Option[LongValueAggMetric] = aggregator.fetch()
  }

  final class Timer {
    private val timestamp          = new AtomicReference[Timestamp]()
    def start(): Unit              = timestamp.set(Timestamp.create())
    def interval(): FiniteDuration = timestamp.get().interval().milliseconds
  }

  final class UninitializedCounter {

    @volatile
    private var counter: AtomicLong = _

    def inc(): Unit            = ensureInitialized(_.getAndIncrement())
    def add(value: Long): Unit = ensureInitialized(_.getAndAdd(value))
    def take(): Option[Long]   = ifInitialized(_.getAndSet(0L))
    def get(): Option[Long]    = ifInitialized(_.get())
    def reset(): Unit          = ifInitialized(_.set(0L))
    def set(value: Long): Unit = ensureInitialized(_.set(value))

    def initialize(): Unit = counter = new AtomicLong(0L)

    private def ifInitialized[@specialized(Long) T](map: AtomicLong => T): Option[T] =
      if (counter ne null) Some(map(counter)) else None

    private def ensureInitialized[@specialized(Long) T](map: AtomicLong => T): T = {
      if (counter eq null) initialize()
      map(counter)
    }

  }

}
