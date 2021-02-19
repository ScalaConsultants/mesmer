package io.scalac.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.AsynchronousInstrument.{ DoubleResult, LongResult }
import io.opentelemetry.api.metrics.{ AsynchronousInstrument, DoubleValueObserver, LongValueObserver }

import io.scalac.extension.metric.MetricObserver

private object Defs {
  type WrappedResult[T]    = (T, Labels) => Unit
  type ResultWrapper[R, T] = R => WrappedResult[T]
  val longResultWrapper: ResultWrapper[LongResult, Long]       = result => result.observe
  val doubleResultWrapper: ResultWrapper[DoubleResult, Double] = result => result.observe
}
import Defs._

final class LongMetricObserverBuilderAdapter(builder: LongValueObserver.Builder)
    extends MetricObserverBuilderAdapter[LongResult, Long](builder, longResultWrapper)

final class DoubleMetricObserverBuilderAdapter(builder: DoubleValueObserver.Builder)
    extends MetricObserverBuilderAdapter[DoubleResult, Double](builder, doubleResultWrapper)

sealed abstract class MetricObserverBuilderAdapter[R <: AsynchronousInstrument.Result, T](
  builder: AsynchronousInstrument.Builder[R],
  wrapper: ResultWrapper[R, T]
) {

  private val observers = collection.mutable.HashMap.empty[Labels, WrappedMetricObserver[T]]

  builder
    .setUpdater(updateAll)
    .build()

  def createObserver(labels: Labels): WrappedMetricObserver[T] = {
    val ref = new WrappedMetricObserver[T](labels)
    synchronized {
      observers.put(labels, ref)
    }
    ref
  }

  def removeObserver(labels: Labels): Unit = synchronized {
    observers.remove(labels)
  }

  private def updateAll(result: R): Unit = {
    val wrapped = wrapper(result)
    observers.values.foreach(_.update(wrapped))
  }

}

final class WrappedMetricObserver[T](labels: Labels) extends MetricObserver[T] {

  private var valueUpdater: Option[MetricObserver.Updater[T]] = None

  def setUpdater(updater: MetricObserver.Updater[T]): Unit = synchronized {
    valueUpdater = Some(updater)
  }

  def update(result: WrappedResult[T]): Unit =
    valueUpdater.foreach(updater => updater(value => result(value, labels)))

}
