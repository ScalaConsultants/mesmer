package io.scalac.extension.util

import scala.concurrent.duration._

class CachedQueryResult[T] private (q: => T, validBy: FiniteDuration = 1.second) {
  private val validByNanos: Long       = validBy.toNanos
  private var lastUpdate: Option[Long] = None
  private var currentValue: Option[T]  = None

  def get: T = {
    // Disclaimer: this double check exists to:
    // 1. have more throughput when update is not needed
    // 2. ensure secure updates
    if (needUpdate) {
      synchronized {
        if (needUpdate) {
          lastUpdate = Some(now)
          currentValue = Some(q)
        }
      }
    }
    currentValue.get
  }

  private def needUpdate: Boolean = lastUpdate.forall(lu => now > (lu + validByNanos))
  private def now: Long           = System.nanoTime()
}
object CachedQueryResult {
  def apply[T](q: => T): CachedQueryResult[T]                       = new CachedQueryResult(q)
  def by[T](validBy: FiniteDuration)(q: => T): CachedQueryResult[T] = new CachedQueryResult(q, validBy)
}
