package io.scalac.extension.metric

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.LongCounter.BoundLongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter.BoundLongUpDownCounter
import io.opentelemetry.api.metrics.LongValueRecorder
import io.opentelemetry.api.metrics.LongValueRecorder.BoundLongValueRecorder

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

object MetricRecorder {

  class BoundWrappedValueRecorder(val underlying: LongValueRecorder,val labels: Labels) extends MetricRecorder[Long] {

    private[this] val bound = underlying.bind(labels)

    override def setValue(value: Long): Unit = bound.record(value)
  }
}
trait UpCounter[T] extends Metric[T] {
  def incValue(value: T): Unit
}
trait Counter[T] extends UpCounter[T] {
  def decValue(value: T): Unit
}

abstract class TrackingMetricRecorder[T](val underlying: MetricRecorder[T]) extends MetricRecorder[T] {
  type Creator
  override def setValue(value: T): Unit = underlying.setValue(value)
}

object TrackingMetricRecorder {
  type Aux[T, C] = TrackingMetricRecorder[T] { type Creator = C }

  def lift[T, C](recorder: MetricRecorder[T]): TrackingMetricRecorder.Aux[T, C] =
    new TrackingMetricRecorder[T](recorder) {
      override type Creator = C
    }
}
