package io.scalac.mesmer.extension.upstream.opentelemetry

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics._

import scala.jdk.CollectionConverters._
import scala.jdk.FunctionConverters._

import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.upstream.LabelsFactory

private object Defs {
  type WrappedResult[T]    = (T, Attributes) => Unit
  type ResultWrapper[R, T] = R => WrappedResult[T]
  val longResultWrapper: ResultWrapper[ObservableLongMeasurement, Long] = result => result.observe
}

import io.scalac.mesmer.extension.upstream.opentelemetry.Defs._

final class GaugeBuilderAdapter[L <: LabelSerializable](builder: LongGaugeBuilder)
    extends MetricObserverBuilderAdapter[ObservableLongMeasurement, Long, L](
      result => builder.buildWithCallback(result.asJava),
      longResultWrapper
    )

final class LongUpDownSumObserverBuilderAdapter[L <: LabelSerializable](builder: LongUpDownCounterBuilder)
    extends MetricObserverBuilderAdapter[ObservableLongMeasurement, Long, L](
      result => builder.buildWithCallback(result.asJava),
      longResultWrapper
    )

final class LongSumObserverBuilderAdapter[L <: LabelSerializable](builder: LongCounterBuilder)
    extends MetricObserverBuilderAdapter[ObservableLongMeasurement, Long, L](
      result => builder.buildWithCallback(result.asJava),
      longResultWrapper
    )

sealed abstract class MetricObserverBuilderAdapter[R <: ObservableMeasurement, T, L <: LabelSerializable](
//  builder: AsynchronousInstrumentBuilder[R],
  register: (R => Unit) => Unit,
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

  private def registerUpdater(observer: => WrappedMetricObserver[T, L]): () => Unit = () => {
    if (!instrumentStarted.get()) {
      if (instrumentStarted.compareAndSet(false, true)) { // no-lock way to ensure this is going to be called once
        register(updateAll)
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

  type Self = WrappedMetricObserver[T, L]

  @volatile
  private var valueUpdater: Option[MetricObserver.Updater[T, L]] = None

  def setUpdater(updater: MetricObserver.Updater[T, L]): Unit = {
    valueUpdater = Some(updater)
    onSet()
  }

  def update(result: WrappedResult[T]): Unit =
    valueUpdater.foreach(updater => updater((value: T, labels: L) => result(value, LabelsFactory.of(labels.serialize))))
}
