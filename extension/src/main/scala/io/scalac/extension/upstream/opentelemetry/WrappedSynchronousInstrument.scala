package io.scalac.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.{ LongCounter, LongUpDownCounter, LongValueRecorder, SynchronousInstrument }
import io.scalac.extension.metric.{ Counter, MetricRecorder, UpCounter }

sealed trait WrappedSynchronousInstrument[L] {
  private[opentelemetry] def underlying: SynchronousInstrument[_]
  private[opentelemetry] def labels: Labels
}

case class WrappedLongValueRecorder(underlying: LongValueRecorder, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with MetricRecorder[Long] {
  private[this] lazy val bound = underlying.bind(labels)

  override def setValue(value: Long): Unit = bound.record(value)
}

case class WrappedUpDownCounter(underlying: LongUpDownCounter, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with Counter[Long] {

  private[this] lazy val bound = underlying.bind(labels)

  override def decValue(value: Long): Unit = bound.add(-value)

  override def incValue(value: Long): Unit = bound.add(value)
}

case class WrappedCounter(underlying: LongCounter, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with UpCounter[Long] {

  private[this] lazy val bound = underlying.bind(labels)

  override def incValue(value: Long): Unit = bound.add(value)
}
