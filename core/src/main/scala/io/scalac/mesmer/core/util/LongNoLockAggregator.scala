package io.scalac.mesmer.core.util

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

import io.scalac.mesmer.core.util.MinMaxSumCountAggregation.LongMinMaxSumCountAggregationImpl
import io.scalac.mesmer.core.util.TimeSeries.LongTimeSeries

// TODO Can we generalize it?
final class LongNoLockAggregator(val maxSize: Int = 100, val compactionRemainingSize: Int = 25) {

  private[this] val aggRef        = new AtomicReference[Option[LongMinMaxSumCountAggregationImpl]](None)
  private[this] val reentrantLock = new ReentrantLock()

  @volatile
  private var compacting: Boolean = false

  private[this] val queue = new ArrayBlockingQueue[Long](maxSize)

  /**
   * Push amount of nonoseconds
   * @param value
   */
  def push(value: Interval): Unit = {
    queue.offer(value.toNano)

    if (queue.remainingCapacity() < compactionRemainingSize && !compacting) {
      failFastCompact()
    }
  }

  def fetch(): Option[LongMinMaxSumCountAggregationImpl] = {
    val snapshot = aggRef.get()
    if (compacting) { // producer is compacting queue, return stale data
      snapshot
    } else {
      if (failFastCompact()) {
        val freshData = aggRef.get()
        aggRef.set(None)
        freshData
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
          .fold(aggRef.set(Some(LongMinMaxSumCountAggregationImpl.fromTimeSeries(ts))))(agg =>
            aggRef.set(Some(agg.sum(ts)))
          )
        true
      } finally reentrantLock.unlock()
    } else false
}
