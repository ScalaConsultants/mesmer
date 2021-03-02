package io.scalac.extension.util.probe

import scala.concurrent.duration._

import akka.actor.Cancellable
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

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

}

sealed trait AbstractTestProbeWrapper {
  type Cmd
  def probe: TestProbe[Cmd]
}

case class CounterTestProbeWrapper(
  private val _probe: TestProbe[CounterCommand],
  private val supervisor: Option[TestProbe[CounterCommand]] = None
) extends AbstractTestProbeWrapper
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
    extends AbstractTestProbeWrapper
    with MetricRecorder[Long] {
  override type Cmd = MetricRecorderCommand

  override def probe: TestProbe[MetricRecorderCommand] = _probe

  override def setValue(value: Long): Unit = _probe.ref ! MetricRecorded(value)
}

case class ObserverTestProbeWrapper(probe: TestProbe[MetricObserverCommand], collector: ObserverCollector)(
  implicit system: ActorSystem[_]
) extends AbstractTestProbeWrapper
    with MetricObserver[Long] {

  type Cmd = MetricObserverCommand

  def setUpdater(cb: MetricObserver.Updater[Long]): Unit =
    collector.update(probe, () => cb(value => probe.ref ! MetricObserved(value)))

}
