package io.scalac.mesmer.core.util

import scala.concurrent.duration.FiniteDuration

import io.scalac.mesmer.core.util.Timestamp.moveTimestamp

final class Interval(private val nanos: Long) extends AnyVal {
  def toMillis: Long = math.floorDiv(nanos, 1_000_000)
  def toMicro: Long  = math.floorDiv(nanos, 1_000)
  def toNano: Long   = nanos
}

/**
 * For performance and testing reasons [[Timestamp]] is implemented as value class but should be treated as abstract
 * type with its only public member being interval method. No direct access of [[value]] is recommended.
 *
 * @param value
 *   created by monotonic clock. Use should not make any assumptions about this value and should be treated as
 *   implementation detail
 */
class Timestamp(private[Timestamp] val value: Long) extends AnyVal {
  def interval(): Interval                    = interval(Timestamp.create())
  def interval(finished: Timestamp): Interval = Timestamp.interval(this, finished)

  /**
   * This is created only for testing
   *
   * @param offset
   *   to add to current timestamp
   * @return
   *   new Timestamp that indicate moment in time after offset ms
   */
  private[scalac] def plus(offset: FiniteDuration): Timestamp = moveTimestamp(this, offset.toNanos)

  /**
   * This is created only for testing
   *
   * @param offset
   *   to remove from current timestamp
   * @return
   *   new Timestamp that indicate moment in time offset ms before
   */
  private[scalac] def minus(offset: FiniteDuration): Timestamp = moveTimestamp(this, -offset.toNanos)

  private[scalac] def >(other: Timestamp): Boolean         = value > other.value
  private[scalac] def +(offset: FiniteDuration): Timestamp = plus(offset)
}

object Timestamp {

  /**
   * @return
   *   new [[Timestamp]] instance representing current point of execution. Should be called before and after measured
   *   code is executed
   */
  def create(): Timestamp = new Timestamp(System.nanoTime())

  /**
   * Calculates interval time in ms between two timestamps
   *
   * @param start
   *   Timestamp created when event started
   * @param finished
   *   Timestamp created when event finished
   * @return
   *   a latency between events in milliseconds
   */
  def interval(start: Timestamp, finished: Timestamp): Interval =
    new Interval(math.abs(finished.value - start.value))

  private def moveTimestamp(timestamp: Timestamp, nanos: Long): Timestamp = new Timestamp(timestamp.value + nanos)

  implicit val ordering: Ordering[Timestamp] = {
    val longOrdering = implicitly[Ordering[Long]]
    (a, b) => longOrdering.compare(a.value, b.value)
  }
}
