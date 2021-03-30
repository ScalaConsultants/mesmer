package io.scalac.extension.upstream.opentelemetry

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

import scala.jdk.CollectionConverters._

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.AsynchronousInstrument
import io.opentelemetry.api.metrics.AsynchronousInstrument.LongResult
import io.opentelemetry.api.metrics._

import io.scalac.core.LabelSerializable
import io.scalac.extension.metric.MetricObserver
import io.scalac.extension.upstream.LabelsFactory

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

sealed abstract class MetricObserverBuilderAdapter[R <: AsynchronousInstrument.Result, T, L <: LabelSerializable](
  builder: AsynchronousInstrument.Builder[R],
  wrapper: ResultWrapper[R, T]
) {

  private[this] val instrumentStarted = new AtomicBoolean()

  //  performance bottleneck is there are many writes in progress, but we work under assumption
  //  that observers will be mostly iterated on
  private val observers = new CopyOnWriteArrayList[WrappedMetricObserver[T, L]]().asScala

  def createObserver: UnregisteredInstrument[WrappedMetricObserver[T, L]] = { root =>
    lazy val observer: WrappedMetricObserver[T, L] =
      WrappedMetricObserver[T, L](registerUpdater(observer)) // defer adding observer to iterator

    root.registerUnbind(() => observers.subtractOne(observer))
    observer
  }

  private def registerUpdater(observer: WrappedMetricObserver[T, L]): () => Unit = () => {
    if (instrumentStarted.get()) {
      if (instrumentStarted.compareAndSet(false, true)) { // no-lock way to ensure this is going to be called once
        builder
          .setUpdater(updateAll)
          .build()
      }
    }
    observers += observer
  }

  private def updateAll(result: R): Unit =
    observers.foreach(_.update(wrapper(result)))
}

final case class WrappedMetricObserver[T, L <: LabelSerializable] private (onSet: () => Unit)
    extends MetricObserver[T, L]
    with WrappedInstrument {

  override type Self = WrappedMetricObserver[T, L]

  @volatile
  private var valueUpdater: Option[MetricObserver.Updater[T, L]] = None

  def setUpdater(updater: MetricObserver.Updater[T, L]): Unit = {
    valueUpdater = Some(updater)
    onSet()
  }

  def update(result: WrappedResult[T]): Unit =
    valueUpdater.foreach(updater => updater((value: T, labels: L) => result(value, LabelsFactory.of(labels.serialize))))
}
