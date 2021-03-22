package io.scalac.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.{ LongCounter, LongUpDownCounter, LongValueRecorder, SynchronousInstrument }
import io.scalac.extension.metric._

trait SynchronousInstrumentFactory {
  private[upstream] def metricRecorder(
    underlying: LongValueRecorder,
    labels: Labels
  ): UnregisteredSynchronousInstrument[WrappedSynchronousInstrument[Long] with MetricRecorder[Long]] = {
    val instrument = WrappedLongValueRecorder(underlying, labels)
    root => {
      root.registerUnbind(instrument)
      instrument
    }
  }

  private[upstream] def counter(
    underlying: LongCounter,
    labels: Labels
  ): UnregisteredSynchronousInstrument[WrappedSynchronousInstrument[Long] with Counter[Long]] = {
    val instrument = WrappedCounter(underlying, labels)
    root => {
      root.registerUnbind(instrument)
      instrument
    }
  }

  private[upstream] def upDownCounter(
    underlying: LongUpDownCounter,
    labels: Labels
  ): UnregisteredSynchronousInstrument[WrappedSynchronousInstrument[Long] with UpDownCounter[Long]] = {
    val instrument = WrappedUpDownCounter(underlying, labels)
    root => {
      root.registerUnbind(instrument)
      instrument
    }
  }
}

/**
 * Using this as intermediate object prevent from creating instruments that are never bound
 */
trait UnregisteredSynchronousInstrument[T <: WrappedSynchronousInstrument[_]] {
  def register(root: UnbindRoot): T
}

sealed trait WrappedSynchronousInstrument[L] extends Unbind {

  private[extension] def underlying: SynchronousInstrument[_]
  private[extension] def labels: Labels
}

private[opentelemetry] final case class WrappedLongValueRecorder(underlying: LongValueRecorder, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with MetricRecorder[Long] {
  private[this] lazy val bound = underlying.bind(labels)

  override def setValue(value: Long): Unit = bound.record(value)

  def unbind(): Unit = bound.unbind()
}

private[opentelemetry] final case class WrappedUpDownCounter(underlying: LongUpDownCounter, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with UpDownCounter[Long] {

  private[this] lazy val bound = underlying.bind(labels)

  override def decValue(value: Long): Unit = bound.add(-value)

  override def incValue(value: Long): Unit = bound.add(value)

  def unbind(): Unit = bound.unbind()
}

private[opentelemetry] final case class WrappedCounter(underlying: LongCounter, labels: Labels)
    extends WrappedSynchronousInstrument[Long]
    with Counter[Long] {

  private[this] lazy val bound = underlying.bind(labels)

  override def incValue(value: Long): Unit = bound.add(value)

  def unbind(): Unit = bound.unbind()
}
