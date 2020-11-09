package io.scalac.extension.metric

import io.opentelemetry.metrics.LongUpDownCounter.BoundLongUpDownCounter
import io.opentelemetry.metrics.LongValueRecorder.BoundLongValueRecorder
import io.opentelemetry.metrics.SynchronousInstrument.BoundInstrument
import io.scalac.extension.upstream.Metric


sealed trait Metric[T]

object Metric {
  implicit class OpenTelemetryOps[T <: BoundInstrument](instrument: T) {
    def toMetricRecorder(
                          implicit ev: T =:= BoundLongValueRecorder
                        ): MetricRecorder[Long] = (value: Long) => ev(instrument).record(value)

    def toCounter(implicit ev: T =:= BoundLongUpDownCounter): Counter[Long] =
      new Counter[Long] {
        private val openTelemetryCounter = ev(instrument)
        override def incValue(value: Long): Unit =
          openTelemetryCounter.add(value)

        override def decValue(value: Long): Unit =
          openTelemetryCounter.add(-value)
      }
  }
}

trait MetricRecorder[T] extends Metric[T] {
  def setValue(value: T): Unit
}

trait Counter[T] extends Metric[T] {
  def incValue(value: T): Unit
  def decValue(value: T): Unit
}
