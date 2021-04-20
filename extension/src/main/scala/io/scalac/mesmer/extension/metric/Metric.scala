package io.scalac.mesmer.extension.metric

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

trait MetricObserver[T, L] extends Metric[T] {
  def setUpdater(updater: MetricObserver.Updater[T, L]): Unit
}

object MetricObserver {
  type Updater[T, L] = MetricObserver.Result[T, L] => Unit

  trait Result[T, L] {
    def observe(value: T, labels: L): Unit
  }
}
