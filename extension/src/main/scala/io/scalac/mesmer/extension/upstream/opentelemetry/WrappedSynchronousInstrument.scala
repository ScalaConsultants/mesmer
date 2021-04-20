package io.scalac.mesmer.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.LongValueRecorder
import io.opentelemetry.api.metrics.SynchronousInstrument

import io.scalac.mesmer.extension.metric._

trait SynchronousInstrumentFactory {

//  private def wrappedLongValueFactor()
  private[upstream] def metricRecorder(
    underlying: LongValueRecorder,
    labels: Labels
  ): UnregisteredInstrument[WrappedLongValueRecorder] = { root =>
    val instrument = WrappedLongValueRecorder(underlying, labels)
    root.registerUnbind(instrument)
    instrument
  }

  private[upstream] def counter(
    underlying: LongCounter,
    labels: Labels
  ): UnregisteredInstrument[WrappedCounter] = { root =>
    val instrument = WrappedCounter(underlying, labels)
    root.registerUnbind(instrument)
    instrument
  }

  private[upstream] def upDownCounter(
    underlying: LongUpDownCounter,
    labels: Labels
  ): UnregisteredInstrument[WrappedUpDownCounter] = { root =>
    val instrument = WrappedUpDownCounter(underlying, labels)
    root.registerUnbind(instrument)
    instrument
  }
}

sealed trait WrappedSynchronousInstrument[L] extends Unbind with WrappedInstrument {

  private[extension] def underlying: SynchronousInstrument[_]
  private[extension] def labels: Labels
}

final case class WrappedLongValueRecorder private[opentelemetry] (underlying: LongValueRecorder, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with MetricRecorder[Long] {
  type Self = WrappedLongValueRecorder

  private[this] lazy val bound = underlying.bind(labels)

  def setValue(value: Long): Unit = bound.record(value)

  def unbind(): Unit = bound.unbind()
}

final case class WrappedUpDownCounter private[opentelemetry] (underlying: LongUpDownCounter, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with UpDownCounter[Long] {
  type Self = WrappedUpDownCounter

  private[this] lazy val bound = underlying.bind(labels)

  def decValue(value: Long): Unit = bound.add(-value)

  def incValue(value: Long): Unit = bound.add(value)

  def unbind(): Unit = bound.unbind()
}

final case class WrappedCounter private[opentelemetry] (underlying: LongCounter, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with Counter[Long] {
  type Self = WrappedCounter

  private[this] lazy val bound = underlying.bind(labels)

  def incValue(value: Long): Unit = bound.add(value)

  def unbind(): Unit = bound.unbind()
}
