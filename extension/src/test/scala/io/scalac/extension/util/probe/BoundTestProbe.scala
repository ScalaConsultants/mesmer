package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import io.scalac.core.LabelSerializable
import io.scalac.extension.metric.MetricObserver.Updater
import io.scalac.extension.metric._
import io.scalac.extension.util.probe.BoundTestProbe._

object BoundTestProbe {

  sealed trait MetricRecorderCommand

  final case class MetricRecorded(value: Long) extends MetricRecorderCommand

  sealed trait CounterCommand

  final case class Inc(value: Long) extends CounterCommand

  final case class Dec(value: Long) extends CounterCommand

  sealed trait MetricObserverCommand[L]

  final case class MetricObserved[L](value: Long, labels: L) extends MetricObserverCommand[L]

}

sealed trait AbstractTestProbeWrapper {
  type Cmd
  def probe: TestProbe[Cmd]
}

sealed trait SyncTestProbeWrapper extends AbstractTestProbeWrapper

case class UpDownCounterTestProbeWrapper(
  private val _probe: TestProbe[CounterCommand],
  private val supervisor: Option[TestProbe[CounterCommand]] = None
) extends SyncTestProbeWrapper
    with UpDownCounter[Long]
    with Counter[Long] {
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

case class ObserverTestProbeWrapper[L <: LabelSerializable](
  probe: TestProbe[MetricObserverCommand[L]],
  collector: ObserverCollector
) extends AsyncTestProbe[MetricObserver.Updater[Long, L]]
    with MetricObserver[Long, L] {

  type Cmd = MetricObserverCommand[L]

  protected def handleCallback(updater: Updater[Long, L]): Unit =
    updater((value, labels) => probe.ref ! MetricObserved(value, labels))

}
