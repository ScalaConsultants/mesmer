package io.scalac.extension.util

import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.TimeSeries.LongTimeSeries

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

// TODO Can we generalize it?
final class LongNoLockAggregator(val maxSize: Int = 100, val compactionRemainingSize: Int = 25) {

  private[this] val aggRef        = new AtomicReference[Option[LongValueAggMetric]](None)
  private[this] val reentrantLock = new ReentrantLock()

  @volatile
  private var compacting: Boolean = false

  private[this] val queue = new ArrayBlockingQueue[Long](maxSize)

  def push(value: Long): Unit = {
    queue.offer(value)

    if (queue.remainingCapacity() < compactionRemainingSize && !compacting) {
      failFastCompact()
    }
  }

  def fetch(): Option[LongValueAggMetric] = {
    val snapshot = aggRef.get()
    if (compacting) { // producer is compacting queue, return stale data
      snapshot
    } else {
      if (failFastCompact()) {
        aggRef.get()
      } else snapshot
    }
  }

  /**
   * Compact that will try once to acquire the lock
   */
  private def failFastCompact(): Boolean =
    if (queue.size() > 0 && reentrantLock.tryLock()) {
      try {
        compacting = true
        val listBuffer = ListBuffer.empty[Long]
        queue.drainTo(listBuffer.asJava)
        compacting = false
        val ts = new LongTimeSeries(listBuffer.toSeq)
        aggRef
          .get()
          .fold(aggRef.set(Some(LongValueAggMetric.fromTimeSeries(ts))))(agg => aggRef.set(Some(agg.combine(ts))))
        true
      } finally reentrantLock.unlock()
    } else false
}
