package io.scalac.extension.util

import scala.concurrent.duration._

import io.scalac.core.util.Timestamp

class CachedQueryResult[T] private (q: => T, validBy: FiniteDuration = 1.second) {
  @volatile
  private var lastUpdate: Option[Timestamp] = None
  private var currentValue: Option[T]       = None

  def get: T = {
    // Disclaimer: this double check exists to:
    // 1. have more throughput when update is not needed
    // 2. ensure secure updates
    if (needUpdate) {
      synchronized {
        if (needUpdate) {
          currentValue = Some(q)
          lastUpdate = Some(now)
        }
      }
    }
    currentValue.get
  }

  private def needUpdate: Boolean = lastUpdate.forall(lu => now > lu + validBy)
  private def now: Timestamp      = Timestamp.create()
}

object CachedQueryResult {
  def apply[T](q: => T): CachedQueryResult[T]                       = new CachedQueryResult(q)
  def by[T](validBy: FiniteDuration)(q: => T): CachedQueryResult[T] = new CachedQueryResult(q, validBy)
}
