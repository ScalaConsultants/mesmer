package io.scalac.extension.util.probe

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.{HttpMetricMonitor, MetricRecorder, UpCounter}
import io.scalac.extension.util.TestProbeSynchronized
import io.scalac.extension.util.probe.BoundTestProbe.{CounterCommand, MetricRecorderCommand}

import scala.collection.concurrent.{Map => CMap}
import scala.jdk.CollectionConverters._

class HttpMetricsTestProbe(implicit val system: ActorSystem[_]) extends HttpMetricMonitor {
  override type Bound = BoundHttpProbes
  import HttpMetricMonitor._

  val globalRequestCounter: TestProbe[CounterCommand] = TestProbe[CounterCommand]()

  private[this] val monitors: CMap[Labels, BoundHttpProbes] = new ConcurrentHashMap[Labels, BoundHttpProbes]().asScala
  private[this] val _binds: AtomicInteger                    = new AtomicInteger(0)

  override def bind(labels: Labels): BoundHttpProbes = {
    _binds.addAndGet(1)
    monitors.getOrElseUpdate(labels, createBoundProbes)
  }

  def probes(labels: Labels): Option[BoundHttpProbes] = monitors.get(labels)
  def boundLabels: Set[Labels]                           = monitors.keys.toSet
  def boundSize: Int                                     = monitors.size
  def binds: Int                                         = _binds.get()
  private def createBoundProbes: BoundHttpProbes         = new BoundHttpProbes(TestProbe(), TestProbe())

  class BoundHttpProbes(
    val requestTimeProbe: TestProbe[MetricRecorderCommand],
    val requestCounterProbe: TestProbe[CounterCommand]
  ) extends BoundMonitor
      with TestProbeSynchronized {

    override def requestTime: MetricRecorder[Long] with AbstractTestProbeWrapper =
      RecorderTestProbeWrapper(requestTimeProbe)

    override def requestCounter: UpCounter[Long] with AbstractTestProbeWrapper =
      CounterTestProbeWrapper(requestCounterProbe, Some(globalRequestCounter))

    override def unbind(): Unit = ()
  }
}
