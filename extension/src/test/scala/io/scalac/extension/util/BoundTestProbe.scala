package io.scalac.extension.util

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.{ ClusterMetricsMonitor, Counter, MetricRecorder, UpCounter }
import io.scalac.extension.model.Node
import io.scalac.extension.util.BoundTestProbe._

object BoundTestProbe {
  sealed trait MetricRecorderCommand

  case class MetricRecorded(value: Long) extends MetricRecorderCommand

  sealed trait CounterCommand

  case class Inc(value: Long) extends CounterCommand

  case class Dec(value: Long) extends CounterCommand

}

trait BoundTestProbe {

  implicit protected class testProbeMetricRecorderOps(val probe: TestProbe[MetricRecorderCommand]) {
    def toMetricRecorder: MetricRecorder[Long] = (value: Long) => probe.ref ! MetricRecorded(value)
  }

  implicit protected class testProbeCounterOps(val probe: TestProbe[CounterCommand]) {
    def toCounter: Counter[Long] = new Counter[Long] {
      override def incValue(value: Long): Unit = probe.ref ! Inc(value)

      override def decValue(value: Long): Unit = probe.ref ! Dec(value)
    }
  }

  implicit protected class testProbeUpCounterOps(val probe: TestProbe[CounterCommand]) {
    def toUpCounter: UpCounter[Long] = value => probe.ref ! Inc(value)
  }

}

class ClusterMetricsTestProbe private (
  val shardPerRegionsProbe: TestProbe[MetricRecorderCommand],
  val entityPerRegionProbe: TestProbe[MetricRecorderCommand],
  val shardRegionsOnNodeProbe: TestProbe[MetricRecorderCommand],
  val reachableNodesProbe: TestProbe[CounterCommand],
  val unreachableNodesProbe: TestProbe[CounterCommand],
  val nodeDownProbe: TestProbe[CounterCommand]
) extends ClusterMetricsMonitor
    with BoundTestProbe {

  override type Bound = BoundMonitor

  override def bind(node: Node): Bound = new BoundMonitor {

    override type Instrument[L] = Any

    override def atomically[A, B](first: Any, second: Any): (A, B) => Unit = ???

    override val shardPerRegions: MetricRecorder[Long] = shardPerRegionsProbe.toMetricRecorder

    override val entityPerRegion: MetricRecorder[Long] = entityPerRegionProbe.toMetricRecorder

    override val shardRegionsOnNode: MetricRecorder[Long] = shardRegionsOnNodeProbe.toMetricRecorder

    override val reachableNodes: Counter[Long] = reachableNodesProbe.toCounter

    override val unreachableNodes: Counter[Long] = unreachableNodesProbe.toCounter

    override val nodeDown: UpCounter[Long] = nodeDownProbe.toUpCounter
  }
}

object ClusterMetricsTestProbe {
  def apply()(implicit system: ActorSystem[_]): ClusterMetricsTestProbe = {
    val shardPerRegionsProbe    = TestProbe[MetricRecorderCommand]("shardPerRegionsProbe")
    val entityPerRegionProbe    = TestProbe[MetricRecorderCommand]("entityPerRegionProbe")
    val shardRegionsOnNodeProbe = TestProbe[MetricRecorderCommand]("shardRegionsOnNodeProbe")
    val reachableNodesProbe     = TestProbe[CounterCommand]("reachableNodesProbe")
    val unreachableNodesProbe   = TestProbe[CounterCommand]("unreachableNodesProbe")
    val nodeDownProbe           = TestProbe[CounterCommand]("nodeDownProbe")
    new ClusterMetricsTestProbe(
      shardPerRegionsProbe,
      entityPerRegionProbe,
      shardRegionsOnNodeProbe,
      reachableNodesProbe,
      unreachableNodesProbe,
      nodeDownProbe
    )
  }
}
