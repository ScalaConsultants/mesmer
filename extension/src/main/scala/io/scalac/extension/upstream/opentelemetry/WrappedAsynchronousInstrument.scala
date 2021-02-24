package io.scalac.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.AsynchronousInstrument.{ DoubleResult, LongResult }
import io.opentelemetry.api.metrics.{ AsynchronousInstrument, _ }
import io.scalac.extension.metric.MetricObserver.LazyUpdater
import io.scalac.extension.metric.{ LazyMetricObserver, MetricObserver }

private object Defs {
  type WrappedResult[T]    = (T, Labels) => Unit
  type ResultWrapper[R, T] = R => WrappedResult[T]
  val longResultWrapper: ResultWrapper[LongResult, Long]       = result => result.observe
  val doubleResultWrapper: ResultWrapper[DoubleResult, Double] = result => result.observe
}
import io.scalac.extension.upstream.opentelemetry.Defs._

final class LongMetricObserverBuilderAdapter(builder: LongValueObserver.Builder)
    extends MetricObserverBuilderAdapter[LongResult, Long](builder, longResultWrapper)

final class DoubleMetricObserverBuilderAdapter(builder: DoubleValueObserver.Builder)
    extends MetricObserverBuilderAdapter[DoubleResult, Double](builder, doubleResultWrapper)

final class LongUpDownSumObserverBuilderAdapter(builder: LongUpDownSumObserver.Builder)
    extends MetricObserverBuilderAdapter[LongResult, Long](builder, longResultWrapper)

final class LongSumObserverBuilderAdapter(builder: LongSumObserver.Builder)
    extends MetricObserverBuilderAdapter[LongResult, Long](builder, longResultWrapper)

sealed abstract class MetricObserverBuilderAdapter[R <: AsynchronousInstrument.Result, T](
  builder: AsynchronousInstrument.Builder[R],
  wrapper: ResultWrapper[R, T]
) {

  private val observers = collection.mutable.HashMap.empty[Labels, WrappedMetricObserver[T]]

  builder
    .setUpdater(updateAll)
    .build()

  def createObserver(labels: Labels): WrappedMetricObserver[T] =
    observers.getOrElseUpdate(labels, new WrappedMetricObserver[T](labels))

  def removeObserver(labels: Labels): Unit =
    observers.remove(labels)

  private def updateAll(result: R): Unit = {
    val wrapped = wrapper(result)
    observers.values.foreach(_.update(wrapped))
  }
}

final class WrappedMetricObserver[T](labels: Labels) extends MetricObserver[T] {

  private var valueUpdater: Option[MetricObserver.Updater[T]] = None

  def setUpdater(updater: MetricObserver.Updater[T]): Unit =
    valueUpdater = Some(updater)

  def update(result: WrappedResult[T]): Unit =
    valueUpdater.foreach(updater => updater((value: T) => result(value, labels)))
}

trait OpenTelemetryLabelsConverter[L] extends (L => Labels)

final class LazyWrappedMetricUpdater[L: OpenTelemetryLabelsConverter](
  val builder: AsynchronousInstrument.Builder[LongResult]
) extends LazyMetricObserver[Long, L] {
  private[this] var instrument: Option[AsynchronousInstrument] = None
  override def setUpdater(updater: LazyUpdater[Long, L]): Unit =
    instrument.fold {
      instrument = Some(builder.setUpdater { consumer =>
        updater { (result, labels) =>
          consumer.observe(result, implicitly[OpenTelemetryLabelsConverter[L]].apply(labels))
        }
      }.build())
    }(_ => throw new IllegalStateException("Update function can be set only once"))
}
