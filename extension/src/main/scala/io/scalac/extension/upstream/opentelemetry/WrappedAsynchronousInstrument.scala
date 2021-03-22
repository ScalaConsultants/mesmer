package io.scalac.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.AsynchronousInstrument.LongResult
import io.opentelemetry.api.metrics.{ AsynchronousInstrument, _ }
import io.scalac.core.LabelSerializable
import io.scalac.extension.metric.{ MetricObserver, UnbindRoot }
import io.scalac.extension.upstream.LabelsFactory

import scala.collection.mutable

private object Defs {
  type WrappedResult[T]    = (T, Labels) => Unit
  type ResultWrapper[R, T] = R => WrappedResult[T]
  val longResultWrapper: ResultWrapper[LongResult, Long] = result => result.observe
}

import io.scalac.extension.upstream.opentelemetry.Defs._

final class LongMetricObserverBuilderAdapter[L <: LabelSerializable](builder: LongValueObserver.Builder)
    extends MetricObserverBuilderAdapter[LongResult, Long, L](builder, longResultWrapper)

final class LongUpDownSumObserverBuilderAdapter[L <: LabelSerializable](builder: LongUpDownSumObserver.Builder)
    extends MetricObserverBuilderAdapter[LongResult, Long, L](builder, longResultWrapper)

final class LongSumObserverBuilderAdapter[L <: LabelSerializable](builder: LongSumObserver.Builder)
    extends MetricObserverBuilderAdapter[LongResult, Long, L](builder, longResultWrapper)

trait UnregisteredMetricObserver[T, L] {
  def register(root: UnbindRoot): MetricObserver[T, L]
}

sealed abstract class MetricObserverBuilderAdapter[R <: AsynchronousInstrument.Result, T, L <: LabelSerializable](
  builder: AsynchronousInstrument.Builder[R],
  wrapper: ResultWrapper[R, T]
) {

  private val observers = mutable.ListBuffer.empty[WrappedMetricObserver[T, L]]

  builder
    .setUpdater(updateAll)
    .build()

  def createObserver: UnregisteredMetricObserver[T, L] = {
    val observer = WrappedMetricObserver[T, L]()
    root => {
      root.registerUnbind(() =>
        observers.filterInPlace(_ == observer)
      ) // TODO this is inefficient for large observers number
      observer
    }
  }

  private def updateAll(result: R): Unit =
    observers.foreach(_.update(wrapper(result)))
}

final case class WrappedMetricObserver[T, L <: LabelSerializable]() extends MetricObserver[T, L] {

  private var valueUpdater: Option[MetricObserver.Updater[T, L]] = None

  def setUpdater(updater: MetricObserver.Updater[T, L]): Unit =
    valueUpdater = Some(updater)

  def update(result: WrappedResult[T]): Unit =
    valueUpdater.foreach(updater => updater((value: T, labels: L) => result(value, LabelsFactory.of(labels.serialize))))
}
