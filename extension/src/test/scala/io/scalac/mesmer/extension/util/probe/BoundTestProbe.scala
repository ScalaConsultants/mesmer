package io.scalac.mesmer.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe

import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.core.util.probe.ObserverCollector
import io.scalac.mesmer.extension.metric.Counter
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.metric.MetricObserver.Updater
import io.scalac.mesmer.extension.metric.MetricRecorder
import io.scalac.mesmer.extension.metric.UpDownCounter
import io.scalac.mesmer.extension.util.probe.BoundTestProbe._

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
  type Cmd = CounterCommand
  def probe: TestProbe[CounterCommand] = _probe

  def decValue(value: Long): Unit = {
    _probe.ref ! Dec(value)
    supervisor.foreach(_.ref ! Inc(1L))
  }

  def incValue(value: Long): Unit = {
    _probe.ref ! Inc(value)
    supervisor.foreach(_.ref ! Inc(1L))
  }
}

case class RecorderTestProbeWrapper(private val _probe: TestProbe[MetricRecorderCommand])
    extends SyncTestProbeWrapper
    with MetricRecorder[Long] {
  type Cmd = MetricRecorderCommand

  def probe: TestProbe[MetricRecorderCommand] = _probe

  def setValue(value: Long): Unit = _probe.ref ! MetricRecorded(value)
}

sealed trait AsyncTestProbe[T] extends AbstractTestProbeWrapper {

  def probe: TestProbe[Cmd]
  def collector: ObserverCollector

  protected def handleCallback(value: T): Unit

  def setUpdater(callback: T): Unit =
    collector.update(probe, () => handleCallback(callback))
}

final case class ObserverTestProbeWrapper[L](
  probe: TestProbe[MetricObserverCommand[L]],
  collector: ObserverCollector
) extends AsyncTestProbe[MetricObserver.Updater[Long, L]]
    with MetricObserver[Long, L] {

  type Cmd = MetricObserverCommand[L]

  protected def handleCallback(updater: Updater[Long, L]): Unit =
    updater((value, labels) => probe.ref ! MetricObserved(value, labels))

}
