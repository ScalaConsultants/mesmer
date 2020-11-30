package io.scalac.extension.metric

import io.opentelemetry.metrics.LongCounter.BoundLongCounter
import io.opentelemetry.metrics.LongUpDownCounter.BoundLongUpDownCounter
import io.opentelemetry.metrics.LongValueRecorder.BoundLongValueRecorder

sealed trait Metric[T]

object Metric {

  implicit class OpenTelemetryLongRecorderOps(
    val recorder: BoundLongValueRecorder
  ) extends AnyVal {
    def toMetricRecorder(): MetricRecorder[Long] =
      value => recorder.record(value)
  }

  implicit class OpenTelemetryLongUpDownCounter(
    val counter: BoundLongUpDownCounter
  ) extends AnyVal {
    def toCounter(): Counter[Long] = new Counter[Long] {
      override def incValue(value: Long): Unit = counter.add(value)

      override def decValue(value: Long): Unit = counter.add(-value)
    }
  }

  implicit class OpenTelemetryLongUpCounter(val counter: BoundLongCounter) extends AnyVal {
    def toUpCounter(): UpCounter[Long] = value => counter.add(value)
  }
}

trait MetricRecorder[T] extends Metric[T] {
  def setValue(value: T): Unit
}
trait UpCounter[T] extends Metric[T] {
  def incValue(value: T): Unit
}
trait Counter[T] extends UpCounter[T] {
  def decValue(value: T): Unit
}
