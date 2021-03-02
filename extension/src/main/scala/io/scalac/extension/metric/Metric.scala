package io.scalac.extension.metric

import io.scalac.extension.metric.MetricObserver.LazyUpdater

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
  def setUpdater(updater: MetricObserver.Updater[T]): Unit
}

trait LazyMetricObserver[T, L] extends Metric[T] {
  def setUpdater(updater: LazyUpdater[T, L]): Unit
}

object MetricObserver {
  type Updater[T]        = MetricObserver.Result[T] => Unit
  type LazyUpdater[T, L] = MetricObserver.LazyResult[T, L] => Unit

  trait Result[T] {
    def observe(value: T): Unit
  }

  trait LazyResult[T, L] {
    def observe(value: T, labels: L): Unit
  }
}
