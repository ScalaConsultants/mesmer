package io.scalac.extension.util.probe

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.extension.metric.Counter
import io.scalac.extension.metric.MetricRecorder
import io.scalac.extension.metric.PersistenceMetricMonitor
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.util.probe.BoundTestProbe.CounterCommand
import io.scalac.extension.util.probe.BoundTestProbe.MetricRecorderCommand

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

class PersistenceMetricTestProbe(implicit val system: ActorSystem[_])
    extends PersistenceMetricMonitor
    with ConcurrentBoundProbes[Labels]
    with BindCounter
    with GlobalProbe {

  type B = BoundPersistenceProbes

  override val globalCounter: TestProbe[CounterCommand] = TestProbe()

  def bind(labels: Labels): PersistenceMetricMonitor.BoundMonitor =
    counting {
      concurrentBind(labels)
    }

  override protected def createBoundProbes(labels: Labels): BoundPersistenceProbes =
    new BoundPersistenceProbes(TestProbe(), TestProbe(), TestProbe(), TestProbe(), TestProbe())

  class BoundPersistenceProbes(
    val recoveryTimeProbe: TestProbe[MetricRecorderCommand],
    val recoveryTotalProbe: TestProbe[CounterCommand],
    val persistentEventProbe: TestProbe[MetricRecorderCommand],
    val persistentEventTotalProbe: TestProbe[CounterCommand],
    val snapshotProbe: TestProbe[CounterCommand]
  ) extends PersistenceMetricMonitor.BoundMonitor {
    override def recoveryTime: SyncTestProbeWrapper with MetricRecorder[Long] =
      RecorderTestProbeWrapper(recoveryTimeProbe)

    override def recoveryTotal: SyncTestProbeWrapper with Counter[Long] =
      UpDownCounterTestProbeWrapper(recoveryTotalProbe, Some(globalCounter))

    override def persistentEvent: SyncTestProbeWrapper with MetricRecorder[Long] =
      RecorderTestProbeWrapper(persistentEventProbe)

    override def persistentEventTotal: SyncTestProbeWrapper with Counter[Long] =
      UpDownCounterTestProbeWrapper(persistentEventTotalProbe, Some(globalCounter))

    override def snapshot: SyncTestProbeWrapper with Counter[Long] =
      UpDownCounterTestProbeWrapper(snapshotProbe, Some(globalCounter))

    override def unbind(): Unit = ()
  }
}
