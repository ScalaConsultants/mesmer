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

trait CancellableTestProbeWrapper {
  def cancel(): Unit
}

case class ObserverTestProbeWrapper(probe: TestProbe[MetricObserverCommand], ping: FiniteDuration)(
  implicit system: ActorSystem[_]
) extends AbstractTestProbeWrapper
    with MetricObserver[Long]
    with CancellableTestProbeWrapper {

  private var cb: Option[MetricObserver.Updater[Long]] = None
  private var schedule: Option[Cancellable]            = None

  type Cmd = MetricObserverCommand

  def setUpdater(cb: MetricObserver.Updater[Long]): Unit = {
    if (this.cb.isEmpty) scheduleUpdates()
    this.cb = Some(cb)
  }

  private def scheduleUpdates(): Unit = {
    import system.executionContext
    schedule.foreach(_.cancel())
    schedule = Some(
      system.scheduler.scheduleWithFixedDelay(ping / 2, ping)(() =>
        cb.foreach(_.apply(value => probe.ref ! MetricObserved(value)))
      )
    )
  }

  def cancel(): Unit = {
    schedule.foreach(_.cancel())
    schedule = None
    cb = None
  }

}
