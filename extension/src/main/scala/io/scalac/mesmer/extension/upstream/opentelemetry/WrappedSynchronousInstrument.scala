package io.scalac.mesmer.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.LongUpDownCounter

import io.scalac.mesmer.extension.metric._

trait SynchronousInstrumentFactory {

  private[upstream] def histogram(underlying: LongHistogram, attributes: Attributes): WrappedHistogram =
    WrappedHistogram(underlying, attributes)

  private[upstream] def counter(underlying: LongCounter, attributes: Attributes): WrappedCounter =
    WrappedCounter(underlying, attributes)

  private[upstream] def upDownCounter(underlying: LongUpDownCounter, attributes: Attributes): WrappedUpDownCounter =
    WrappedUpDownCounter(underlying, attributes)

  private[upstream] def noopHistogram[T]: WrappedSynchronousInstrument[T] with Histogram[T]         = NoopLongHistogram
  private[upstream] def noopCounter[T]: WrappedSynchronousInstrument[T] with Counter[T]             = NoopCounter
  private[upstream] def noopUpDownCounter[T]: WrappedSynchronousInstrument[T] with UpDownCounter[T] = NoopUpDownCounter
}

sealed trait WrappedSynchronousInstrument[-L] extends WrappedInstrument

sealed trait WrappedNoOp extends WrappedSynchronousInstrument[Any]

case object NoopLongHistogram extends WrappedNoOp with Histogram[Any] {
  def setValue(value: Any): Unit = ()
}

case object NoopCounter extends WrappedNoOp with Counter[Any] {
  def incValue(value: Any): Unit = ()
}

case object NoopUpDownCounter extends WrappedNoOp with UpDownCounter[Any] {
  def decValue(value: Any): Unit = ()

  def incValue(value: Any): Unit = ()
}

final case class WrappedHistogram private[opentelemetry] (underlying: LongHistogram, attributes: Attributes)
    extends WrappedSynchronousInstrument[Long]
    with Histogram[Long] {

  private[this] lazy val bound = underlying

  def setValue(value: Long): Unit = bound.record(value, attributes)
}

final case class WrappedUpDownCounter private[opentelemetry] (underlying: LongUpDownCounter, attributes: Attributes)
    extends WrappedSynchronousInstrument[Long]
    with UpDownCounter[Long] {

  private[this] lazy val bound = underlying

  def decValue(value: Long): Unit = bound.add(-value, attributes)

  def incValue(value: Long): Unit = bound.add(value, attributes)
}

final case class WrappedCounter private[opentelemetry] (underlying: LongCounter, attributes: Attributes)
    extends WrappedSynchronousInstrument[Long]
    with Counter[Long] {

  private[this] lazy val bound = underlying

  def incValue(value: Long): Unit = bound.add(value, attributes)
}
