package io.scalac.extension.util.probe

import akka.actor.Cancellable
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.MetricObserver.Updater
import io.scalac.extension.metric._
import io.scalac.extension.util.probe.BoundTestProbe._

import scala.concurrent.duration._

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

sealed abstract class AsyncTestProbe[T](ping: FiniteDuration)(implicit val system: ActorSystem[_])
    extends AbstractTestProbeWrapper
    with CancellableTestProbeWrapper {

  private var schedule: Option[Cancellable] = None

  protected var callback: Option[T] = None

  protected def handleCallback(value: T): Unit

  protected def scheduleUpdates(): Unit = {
    import system.executionContext
    schedule.foreach(_.cancel())
    schedule = Some(
      system.scheduler.scheduleWithFixedDelay(ping / 2, ping)(() => callback.foreach(handleCallback))
    )
  }

  def cancel(): Unit = {
    schedule.foreach(_.cancel())
    schedule = None
    callback = None
  }

  def setUpdater(cb: T): Unit = {
    if (this.callback.isEmpty) scheduleUpdates()
    this.callback = Some(cb)
  }
}

case class ObserverTestProbeWrapper(probe: TestProbe[MetricObserverCommand], ping: FiniteDuration)(implicit
  system: ActorSystem[_]
) extends AsyncTestProbe[MetricObserver.Updater[Long]](ping)
    with MetricObserver[Long] {

  type Cmd = MetricObserverCommand

  override protected def handleCallback(updater: Updater[Long]): Unit =
    updater(value => probe.ref ! MetricObserved(value))

}

case class LazyObserverTestProbeWrapper[L](probe: TestProbe[LazyMetricsObserved[L]], ping: FiniteDuration)(implicit
  system: ActorSystem[_]
) extends AsyncTestProbe[MetricObserver.LazyUpdater[Long, L]](ping)
    with LazyMetricObserver[Long, L] {

  type Cmd = LazyMetricsObserved[L]

  override protected def handleCallback(updater: MetricObserver.LazyUpdater[Long, L]): Unit =
    updater((value, labels) => probe.ref ! LazyMetricsObserved(value, labels))

}
