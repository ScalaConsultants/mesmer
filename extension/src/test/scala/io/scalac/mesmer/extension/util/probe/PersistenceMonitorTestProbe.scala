package io.scalac.mesmer.extension.util.probe

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

import io.scalac.mesmer.extension.metric.Counter
import io.scalac.mesmer.extension.metric.MetricRecorder
import io.scalac.mesmer.extension.metric.PersistenceMetricsMonitor
import io.scalac.mesmer.extension.metric.PersistenceMetricsMonitor.Labels
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.CounterCommand
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.MetricRecorderCommand

trait BindCounter {
  private[this] val _binds: AtomicInteger = new AtomicInteger(0)
  final protected def counting[B](block: => B): B = {
    _binds.addAndGet(1)
    block
  }
  def binds: Int = _binds.get()
}

trait ConcurrentBoundProbes[L] {

  type B

  private[this] val monitors: CMap[L, B] =
    new ConcurrentHashMap[L, B]().asScala

  protected def createBoundProbes(labels: L): B
  final protected def concurrentBind(labels: L): B = monitors.getOrElseUpdate(labels, createBoundProbes(labels))

  def probes(labels: L): Option[B] = monitors.get(labels)
  def boundLabels: Set[L]          = monitors.keySet.toSet
  def boundSize: Int               = monitors.size

}

trait GlobalProbe {
  def globalCounter: TestProbe[CounterCommand]
}

class PersistenceMonitorTestProbe(implicit val system: ActorSystem[_])
    extends PersistenceMetricsMonitor
    with ConcurrentBoundProbes[Labels]
    with BindCounter
    with GlobalProbe {

  type B = BoundPersistenceProbes

  val globalCounter: TestProbe[CounterCommand] = TestProbe()

  def bind(labels: Labels): PersistenceMetricsMonitor.BoundMonitor =
    counting {
      concurrentBind(labels)
    }

  protected def createBoundProbes(labels: Labels): BoundPersistenceProbes =
    new BoundPersistenceProbes(TestProbe(), TestProbe(), TestProbe(), TestProbe(), TestProbe())

  class BoundPersistenceProbes(
    val recoveryTimeProbe: TestProbe[MetricRecorderCommand],
    val recoveryTotalProbe: TestProbe[CounterCommand],
    val persistentEventProbe: TestProbe[MetricRecorderCommand],
    val persistentEventTotalProbe: TestProbe[CounterCommand],
    val snapshotProbe: TestProbe[CounterCommand]
  ) extends PersistenceMetricsMonitor.BoundMonitor {
    def recoveryTime: SyncTestProbeWrapper with MetricRecorder[Long] =
      RecorderTestProbeWrapper(recoveryTimeProbe)

    def recoveryTotal: SyncTestProbeWrapper with Counter[Long] =
      UpDownCounterTestProbeWrapper(recoveryTotalProbe, Some(globalCounter))

    def persistentEvent: SyncTestProbeWrapper with MetricRecorder[Long] =
      RecorderTestProbeWrapper(persistentEventProbe)

    def persistentEventTotal: SyncTestProbeWrapper with Counter[Long] =
      UpDownCounterTestProbeWrapper(persistentEventTotalProbe, Some(globalCounter))

    def snapshot: SyncTestProbeWrapper with Counter[Long] =
      UpDownCounterTestProbeWrapper(snapshotProbe, Some(globalCounter))

    def unbind(): Unit = ()
  }
}
