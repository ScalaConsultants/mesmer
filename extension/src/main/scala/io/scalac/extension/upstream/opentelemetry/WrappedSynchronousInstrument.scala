package io.scalac.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.{ LongCounter, LongUpDownCounter, LongValueRecorder, SynchronousInstrument }
import io.scalac.extension.metric.{ Counter, MetricRecorder, UpDownCounter }

sealed trait WrappedSynchronousInstrument[L] {
  private[extension] def underlying: SynchronousInstrument[_]
  private[extension] def labels: Labels
  private[extension] def unbind(): Unit
}

case class WrappedLongValueRecorder(underlying: LongValueRecorder, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with MetricRecorder[Long] {
  private[this] lazy val bound = underlying.bind(labels)

  override def setValue(value: Long): Unit = bound.record(value)

  override private[extension] def unbind(): Unit = bound.unbind()
}

case class WrappedUpDownCounter(underlying: LongUpDownCounter, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with UpDownCounter[Long] {

  private[this] lazy val bound = underlying.bind(labels)

  override def decValue(value: Long): Unit = bound.add(-value)

  override def incValue(value: Long): Unit = bound.add(value)

  override private[extension] def unbind(): Unit = bound.unbind()
}

case class WrappedCounter(underlying: LongCounter, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with Counter[Long] {

  private[this] lazy val bound = underlying.bind(labels)

  override def incValue(value: Long): Unit = bound.add(value)

  override private[extension] def unbind(): Unit = bound.unbind()
}
