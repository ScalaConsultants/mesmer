package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.extension.metric.SyncWith.UpdaterPair

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

sealed trait Metric[T]

trait MetricRecorder[T] extends Metric[T] {
  def setValue(value: T): Unit
}

trait Counter[T] extends Metric[T] {
  def incValue(value: T): Unit
}

trait UpDownCounter[T] extends Counter[T] {
  def decValue(value: T): Unit
}

final class SyncWith private (
  private val amount: Int,
  private val updaters: List[
    UpdaterPair[T, L] forSome { type T; type L }
  ] // this guarantees type safety without additional classes
) {

  def `with`[T, L](observer: MetricObserver[T, L])(updater: MetricObserver.Updater[T, L]): SyncWith =
    new SyncWith(amount + 1, (observer, updater) :: updaters)

  def afterAll(effect: => Unit): CountingAfterAll = {

    val counting = new CountingAfterAll(amount, () => effect)

    updaters.foreach { case pair: UpdaterPair[t, l] =>
      val (observer, updater): UpdaterPair[t, l] = pair
      val incUpdater                             = updater andThen (_ => counting.inc())
      observer.setUpdater(incUpdater)
    }
    counting
  }
}

final class CountingAfterAll(expected: Int, effect: () => Unit) {

  private[mesmer] val counter = new AtomicInteger(0)

  @tailrec
  def inc(): Unit = {
    val value = counter.incrementAndGet()
    if (value >= expected) {
      if (counter.compareAndSet(value, 0)) {
        effect()
      } else inc()
    }
  }

}

object SyncWith {
  def apply(): SyncWith = new SyncWith(0, Nil)
  type UpdaterPair[T, L] = (MetricObserver[T, L], MetricObserver.Updater[T, L])

}

trait MetricObserver[T, L] extends Metric[T] {
  def setUpdater(updater: MetricObserver.Updater[T, L]): Unit
}

object MetricObserver {
  type Updater[T, L] = MetricObserver.Result[T, L] => Unit

  trait Result[T, L] {
    def observe(value: T, labels: L): Unit
  }
}
