package io.scalac.mesmer.extension.upstream.opentelemetry

import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.LongValueRecorder
import io.opentelemetry.api.metrics.SynchronousInstrument
import io.opentelemetry.api.metrics.common.Labels

import io.scalac.mesmer.extension.metric._

trait SynchronousInstrumentFactory {

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

  private[upstream] def noopMetricRecorder[T]: WrappedSynchronousInstrument[T] with MetricRecorder[T] =
    NoopLongValueRecorder
  private[upstream] def noopCounter[T]: WrappedSynchronousInstrument[T] with Counter[T]             = NoopCounter
  private[upstream] def noopUpDownCounter[T]: WrappedSynchronousInstrument[T] with UpDownCounter[T] = NoopUpDownCounter
}

sealed trait WrappedSynchronousInstrument[-L] extends Unbind with WrappedInstrument {

  private[extension] def underlying: SynchronousInstrument[_]
}

sealed trait WrappedNoOp extends WrappedSynchronousInstrument[Any] {
  final private[extension] def underlying: SynchronousInstrument[_] = throw new UnsupportedOperationException(
    "Cannot get underlying instrument from noop"
  )
}

case object NoopLongValueRecorder extends WrappedNoOp with MetricRecorder[Any] {

  private[scalac] def unbind(): Unit = ()

  def setValue(value: Any): Unit = ()

  override type Self = Nothing
}

case object NoopCounter extends WrappedNoOp with Counter[Any] {
  def incValue(value: Any): Unit = ()

  private[scalac] def unbind(): Unit = ()

  override type Self = Nothing
}

case object NoopUpDownCounter extends WrappedNoOp with UpDownCounter[Any] {
  def decValue(value: Any): Unit = ()

  private[scalac] def unbind(): Unit = ()

  def incValue(value: Any): Unit = ()

  override type Self = Nothing
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
