package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import io.scalac.extension.metric.MetricObserver.Updater
import io.scalac.extension.metric._
import io.scalac.extension.util.probe.BoundTestProbe._

object BoundTestProbe {

  sealed trait MetricRecorderCommand

  case class MetricRecorded(value: Long) extends MetricRecorderCommand

  sealed trait CounterCommand

  case class Inc(value: Long) extends CounterCommand

  case class Dec(value: Long) extends CounterCommand

  sealed trait MetricObserverCommand

  case class MetricObserved(value: Long) extends MetricObserverCommand

  final case class LazyMetricsObserved[L](value: Long, labels: L)

}

sealed trait AbstractTestProbeWrapper {
  type Cmd
  def probe: TestProbe[Cmd]
}

sealed trait SyncTestProbeWrapper extends AbstractTestProbeWrapper

case class CounterTestProbeWrapper(
  private val _probe: TestProbe[CounterCommand],
  private val supervisor: Option[TestProbe[CounterCommand]] = None
) extends SyncTestProbeWrapper
    with Counter[Long]
    with UpCounter[Long] {
  override type Cmd = CounterCommand
  def probe: TestProbe[CounterCommand] = _probe

  override def decValue(value: Long): Unit = {
    _probe.ref ! Dec(value)
    supervisor.foreach(_.ref ! Inc(1L))
  }

  override def incValue(value: Long): Unit = {
    _probe.ref ! Inc(value)
    supervisor.foreach(_.ref ! Inc(1L))
  }
}

case class RecorderTestProbeWrapper(private val _probe: TestProbe[MetricRecorderCommand])
    extends SyncTestProbeWrapper
    with MetricRecorder[Long] {
  override type Cmd = MetricRecorderCommand

  override def probe: TestProbe[MetricRecorderCommand] = _probe

  override def setValue(value: Long): Unit = _probe.ref ! MetricRecorded(value)
}

sealed trait AsyncTestProbe[T] extends AbstractTestProbeWrapper {

  def probe: TestProbe[Cmd]
  def collector: ObserverCollector

  protected def handleCallback(value: T): Unit

  def setUpdater(callback: T): Unit =
    collector.update(probe, () => handleCallback(callback))
}

case class ObserverTestProbeWrapper(probe: TestProbe[MetricObserverCommand], collector: ObserverCollector)
    extends AsyncTestProbe[MetricObserver.Updater[Long]]
    with MetricObserver[Long] {

  type Cmd = MetricObserverCommand

  protected def handleCallback(updater: Updater[Long]): Unit =
    updater(value => probe.ref ! MetricObserved(value))

}

case class LazyObserverTestProbeWrapper[L](probe: TestProbe[LazyMetricsObserved[L]], collector: ObserverCollector)
    extends AsyncTestProbe[MetricObserver.LazyUpdater[Long, L]]
    with LazyMetricObserver[Long, L] {

  type Cmd = LazyMetricsObserved[L]

  protected def handleCallback(updater: MetricObserver.LazyUpdater[Long, L]): Unit =
    updater((value, labels) => probe.ref ! LazyMetricsObserved(value, labels))

}
