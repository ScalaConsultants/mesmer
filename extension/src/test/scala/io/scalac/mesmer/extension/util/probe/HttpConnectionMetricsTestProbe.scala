package io.scalac.mesmer.extension.util.probe

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

import io.scalac.mesmer.extension.metric.HttpConnectionMetricsMonitor
import io.scalac.mesmer.extension.metric.UpDownCounter
import io.scalac.mesmer.extension.util.TestProbeSynchronized
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.CounterCommand

class HttpConnectionMetricsTestProbe(implicit val system: ActorSystem[_]) extends HttpConnectionMetricsMonitor {

  import HttpConnectionMetricsMonitor._

  val globalConnectionCounter: TestProbe[CounterCommand] = TestProbe[CounterCommand]()

  private[this] val monitors: CMap[Attributes, BoundHttpProbes] =
    new ConcurrentHashMap[Attributes, BoundHttpProbes]().asScala
  private[this] val _binds: AtomicInteger = new AtomicInteger(0)

  def bind(attributes: Attributes): BoundHttpProbes = {
    _binds.addAndGet(1)
    monitors.getOrElseUpdate(attributes, createBoundProbes)
  }

  def probes(attributes: Attributes): Option[BoundHttpProbes] = monitors.get(attributes)
  def boundAttributes: Set[Attributes]                        = monitors.keys.toSet
  def boundSize: Int                                          = monitors.size
  def binds: Int                                              = _binds.get()
  private def createBoundProbes: BoundHttpProbes              = new BoundHttpProbes(TestProbe())

  class BoundHttpProbes(
    val connectionCounterProbe: TestProbe[CounterCommand]
  ) extends BoundMonitor
      with TestProbeSynchronized {

    val connections: UpDownCounter[Long] with SyncTestProbeWrapper =
      UpDownCounterTestProbeWrapper(connectionCounterProbe, Some(globalConnectionCounter))

    def unbind(): Unit = ()
  }
}
