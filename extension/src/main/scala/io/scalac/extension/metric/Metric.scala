package io.scalac.extension.metric

sealed trait Metric[T]

trait MetricRecorder[T] extends Metric[T] {
  def setValue(value: T): Unit
}

trait UpCounter[T] extends Metric[T] {
  def incValue(value: T): Unit
}

trait Counter[T] extends UpCounter[T] {
  def decValue(value: T): Unit
}

trait MetricObserver[T] extends Metric[T] {
  def setUpdater(updater: MetricObserver.Updater[T])
}

object MetricObserver {
  type Updater[T] = MetricObserver.Result[T] => Unit
  trait Result[T] {
    def observe(value: T): Unit
  }
}
