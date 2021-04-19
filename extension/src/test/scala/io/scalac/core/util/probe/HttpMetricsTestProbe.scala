package io.scalac.core.util.probe

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

import io.scalac.core.util.TestProbeSynchronized
import io.scalac.core.util.probe.BoundTestProbe.CounterCommand
import io.scalac.core.util.probe.BoundTestProbe.MetricRecorderCommand
import io.scalac.extension.metric.Counter
import io.scalac.extension.metric.HttpMetricMonitor
import io.scalac.extension.metric.MetricRecorder

class HttpMetricsTestProbe(implicit val system: ActorSystem[_]) extends HttpMetricMonitor {

  import HttpMetricMonitor._

  val globalRequestCounter: TestProbe[CounterCommand] = TestProbe[CounterCommand]()

  private[this] val monitors: CMap[Labels, BoundHttpProbes] = new ConcurrentHashMap[Labels, BoundHttpProbes]().asScala
  private[this] val _binds: AtomicInteger                   = new AtomicInteger(0)

  def bind(labels: Labels): BoundHttpProbes = {
    _binds.addAndGet(1)
    monitors.getOrElseUpdate(labels, createBoundProbes)
  }

  def probes(labels: Labels): Option[BoundHttpProbes] = monitors.get(labels)
  def boundLabels: Set[Labels]                        = monitors.keys.toSet
  def boundSize: Int                                  = monitors.size
  def binds: Int                                      = _binds.get()
  private def createBoundProbes: BoundHttpProbes      = new BoundHttpProbes(TestProbe(), TestProbe())

  class BoundHttpProbes(
    val requestTimeProbe: TestProbe[MetricRecorderCommand],
    val requestCounterProbe: TestProbe[CounterCommand]
  ) extends BoundMonitor
      with TestProbeSynchronized {

    val requestTime: MetricRecorder[Long] with SyncTestProbeWrapper =
      RecorderTestProbeWrapper(requestTimeProbe)

    val requestCounter: Counter[Long] with SyncTestProbeWrapper =
      UpDownCounterTestProbeWrapper(requestCounterProbe, Some(globalRequestCounter))

    def unbind(): Unit = ()
  }
}
