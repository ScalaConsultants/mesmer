package io.scalac.mesmer.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.extension.metric.{ Synchronized => BaseSynchronized }

abstract class Synchronized(private val meter: Meter) extends BaseSynchronized {
  import Synchronized._

  type Instrument[X] = WrappedSynchronousInstrument[X]

  protected val otAttributes: Attributes

  def atomically[A, B](first: Instrument[A], second: Instrument[B]): (A, B) => Unit = { (a, b) =>
    first.putValue(a)
    second.putValue(b)
  }

}

object Synchronized {
  private[opentelemetry] implicit class RecorderExt(private val instrument: WrappedSynchronousInstrument[_])
      extends AnyVal {
    def putValue(value: Any): Unit =
      instrument match {
        case rec @ WrappedHistogram(underlying, _)         => rec.setValue(value.asInstanceOf[Long])
        case counter @ WrappedCounter(underlying, _)       => counter.incValue(value.asInstanceOf[Long])
        case counter @ WrappedUpDownCounter(underlying, _) => counter.incValue(value.asInstanceOf[Long])
        case _: WrappedNoOp                                => // skip any noop monitor
      }
  }
}
