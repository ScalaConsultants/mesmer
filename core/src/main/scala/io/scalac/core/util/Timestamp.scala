package io.scalac.core.util

/**
 * For performance and testing reasons [[Timestamp]] is implemented as value class but should be treated as abstract type with its
 * only public member being interval method. No direct access of [[value]] is recommended.
 *
 * @param value created by monotonic clock. Use should not make any assumptions about this value and should be treated
 *              as implementation detail
 */
class Timestamp(val value: Long) extends AnyVal {
  def interval(finished: Timestamp): Long = Timestamp.interval(this, finished)

  private[scalac] def after(ms: Long): Timestamp = new Timestamp(value + (ms * 1_000_000))

  private[scalac] def before(ms: Long): Timestamp = after(-ms)
}

object Timestamp {

  /**
   *
   * @return new [[Timestamp]] instance representing current point of execution. Should be called before and after
   *         measured code is executed
   */
  def create(): Timestamp = new Timestamp(System.nanoTime())

  /**
   * Calculates interval time in ms between two timestamps
   *
   *  @param start Timestamp created when event started
   *  @param finished Timestamp created when event finished
   *  @return a latency between events in milliseconds
   */
  def interval(start: Timestamp, finished: Timestamp): Long =
    math.floorDiv(math.abs(finished.value - start.value), 1_000_000)
}
