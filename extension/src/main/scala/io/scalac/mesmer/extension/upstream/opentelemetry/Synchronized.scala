package io.scalac.mesmer.extension.upstream.opentelemetry

import io.opentelemetry.api.metrics.BatchRecorder
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.common.Labels

import scala.collection.mutable.ListBuffer

import io.scalac.mesmer.extension.metric.{ Synchronized => BaseSynchronized }

abstract class Synchronized(private val meter: Meter) extends BaseSynchronized {
  import Synchronized._

  type Instrument[X] = WrappedSynchronousInstrument[X]

  protected val otLabels: Labels

  def atomically[A, B](first: Instrument[A], second: Instrument[B]): (A, B) => Unit = { (a, b) =>
    meter
      .newBatchRecorder(extractLabels(otLabels): _*)
      .putValue(first, a)
      .putValue(second, b)
      .record()
  }

  private def extractLabels(labels: Labels): List[String] = {
    val buffer: ListBuffer[String] = ListBuffer.empty
    labels.forEach { case (key, value) =>
      buffer ++= List(key, value)
    }
    buffer.toList
  }
}

object Synchronized {
  private[opentelemetry] implicit class RecorderExt(private val recorder: BatchRecorder) extends AnyVal {
    def putValue[L](instrument: WrappedSynchronousInstrument[L], value: L): BatchRecorder = {
      instrument match {
        case WrappedLongValueRecorder(underlying, _) => recorder.put(underlying, value)
        case WrappedCounter(underlying, _)           => recorder.put(underlying, value)
        case WrappedUpDownCounter(underlying, _)     => recorder.put(underlying, value)
        case _: WrappedNoOp                          => // skip any noop monitor
      }
      recorder
    }
  }
}
