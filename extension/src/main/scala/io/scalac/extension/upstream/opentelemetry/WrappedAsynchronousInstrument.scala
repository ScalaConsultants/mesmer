package io.scalac.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.AsynchronousInstrument.LongResult
import io.opentelemetry.api.metrics.{ AsynchronousInstrument, LongValueObserver }

import io.scalac.extension.metric.MetricObserver

final case class WrappedLongValueObserver(adapter: LongValueObserverAdapter, labels: Labels)
    extends MetricObserver[Long] {

  private val ref = adapter.put(labels)

  def setUpdater(updater: MetricObserver.Updater[Long]): Unit = ref.set(updater)

}

final class LongValueObserverAdapter(builder: LongValueObserver.Builder) {

  private val updaters = collection.mutable.HashMap.empty[Labels, LongValueUpdaterRef]

  builder
    .setUpdater(updateAll)
    .build()

  def put(labels: Labels): LongValueUpdaterRef = {
    val ref = new LongValueUpdaterRef(labels)
    synchronized {
      updaters.put(labels, ref)
    }
    ref
  }

  def remove(labels: Labels): Unit = {
    updaters.values.foreach(_.unset())
    synchronized {
      updaters.remove(labels)
    }
  }

  private def updateAll(result: LongResult): Unit =
    updaters.values.foreach(_.update(result))

}

private trait LongValueUpdater extends (LongResult => Unit)

final class LongValueUpdaterRef(labels: Labels) {

  private var longValueUpdater: Option[LongValueUpdater] = None

  def set(updater: MetricObserver.Updater[Long]): Unit = synchronized {
    longValueUpdater = Some(adapt(updater))
  }

  def unset(): Unit = synchronized {
    longValueUpdater = None
  }

  def update(result: AsynchronousInstrument.LongResult): Unit =
    longValueUpdater.foreach(_.apply(result))

  private def adapt(updater: MetricObserver.Updater[Long]): LongValueUpdater =
    result => updater(value => result.observe(value, labels))

}
