package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.metric.{ Bindable, MetricRecorder, PersistenceMetricMonitor, UpCounter }
import io.scalac.extension.util.TestProbeSynchronized
import io.scalac.extension.util.probe.BoundTestProbe.{ CounterCommand, MetricRecorderCommand }

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

trait BindCounter[L] extends Bindable[L] {
  private[this] val _binds: AtomicInteger = new AtomicInteger(0)
  abstract override def bind(labels: L): Bound = {
    _binds.addAndGet(1)
    super.bind(labels)
  }
  def binds: Int = _binds.get()
}

trait ConcurrentBoundProbes[L] extends Bindable[L] {

  private[this] val monitors: CMap[L, Bound] =
    new ConcurrentHashMap[L, Bound]().asScala

  override def bind(labels: L): Bound = monitors.getOrElseUpdate(labels, createBoundProbes(labels))

  protected def createBoundProbes(labels: L): Bound

  def probes(labels: L): Option[Bound] = monitors.get(labels)
  def boundLabels: Set[L]              = monitors.keySet.toSet
  def boundSize: Int                   = monitors.size
}

trait GlobalProbe {
  def globalCounter: TestProbe[CounterCommand]
}

class PersistenceMetricTestProbe(implicit val system: ActorSystem[_])
    extends PersistenceMetricMonitor
    with ConcurrentBoundProbes[Labels]
    with BindCounter[Labels]
    with GlobalProbe {

  override val globalCounter: TestProbe[CounterCommand] = TestProbe()

  override type Bound = BoundPersistenceProbes

  override protected def createBoundProbes(labels: Labels): BoundPersistenceProbes =
    new BoundPersistenceProbes(TestProbe(), TestProbe(), TestProbe(), TestProbe(), TestProbe())

  class BoundPersistenceProbes(
    val recoveryTimeProbe: TestProbe[MetricRecorderCommand],
    val recoveryTotalProbe: TestProbe[CounterCommand],
    val persistentEventProbe: TestProbe[MetricRecorderCommand],
    val persistentEventTotalProbe: TestProbe[CounterCommand],
    val snapshotProbe: TestProbe[CounterCommand]
  ) extends BoundMonitor
      with TestProbeSynchronized {
    override def recoveryTime: AbstractTestProbeWrapper with MetricRecorder[Long] =
      RecorderTestProbeWrapper(recoveryTimeProbe)

    override def recoveryTotal: AbstractTestProbeWrapper with UpCounter[Long] =
      CounterTestProbeWrapper(recoveryTotalProbe, Some(globalCounter))

    override def persistentEvent: AbstractTestProbeWrapper with MetricRecorder[Long] =
      RecorderTestProbeWrapper(persistentEventProbe)

    override def persistentEventTotal: AbstractTestProbeWrapper with UpCounter[Long] =
      CounterTestProbeWrapper(persistentEventTotalProbe, Some(globalCounter))

    override def snapshot: AbstractTestProbeWrapper with UpCounter[Long] =
      CounterTestProbeWrapper(snapshotProbe, Some(globalCounter))

    override def unbind(): Unit = ()
  }
}
