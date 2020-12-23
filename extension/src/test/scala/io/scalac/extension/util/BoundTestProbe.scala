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

sealed trait AbstractTestProbeWrapper {
  type Cmd
  def probe: TestProbe[Cmd]
}

case class CounterTestProbeWrapper(private val _probe: TestProbe[CounterCommand])
    extends AbstractTestProbeWrapper
    with Counter[Long]
    with UpCounter[Long] {
  override type Cmd = CounterCommand
  def probe: TestProbe[CounterCommand] = _probe

  override def decValue(value: Long): Unit = _probe.ref ! Dec(value)

  override def incValue(value: Long): Unit = _probe.ref ! Inc(value)
}

case class RecorderTestProbeWrapper(private val _probe: TestProbe[MetricRecorderCommand])
    extends AbstractTestProbeWrapper
    with MetricRecorder[Long] {
  override type Cmd = MetricRecorderCommand

  override def probe: TestProbe[MetricRecorderCommand] = _probe

  override def setValue(value: Long): Unit = _probe.ref ! MetricRecorded(value)
}

class ClusterMetricsTestProbe private (
  val shardPerRegionsProbe: TestProbe[MetricRecorderCommand],
  val entityPerRegionProbe: TestProbe[MetricRecorderCommand],
  val shardRegionsOnNodeProbe: TestProbe[MetricRecorderCommand],
  val reachableNodesProbe: TestProbe[CounterCommand],
  val unreachableNodesProbe: TestProbe[CounterCommand],
  val nodeDownProbe: TestProbe[CounterCommand]
) extends ClusterMetricsMonitor {

  override type Bound = BoundMonitor

  override def bind(node: Node): Bound = new BoundMonitor {

    override type Instrument[L] = AbstractTestProbeWrapper

    override def atomically[A, B](first: AbstractTestProbeWrapper, second: AbstractTestProbeWrapper): (A, B) => Unit = {
      def submitValue(value: Long, probe: AbstractTestProbeWrapper): Unit = probe match {
        case counter: CounterTestProbeWrapper =>
          if (value >= 0L) counter.probe.ref ! Inc(value) else counter.probe.ref ! Dec(-value)
        case recorder: RecorderTestProbeWrapper => recorder.probe.ref ! MetricRecorded(value)
      }
      (a, b) => {
        submitValue(a.asInstanceOf[Long], first)
        submitValue(b.asInstanceOf[Long], second)
      }
    }

    override val shardPerRegions: MetricRecorder[Long] with AbstractTestProbeWrapper = RecorderTestProbeWrapper(
      shardPerRegionsProbe
    )

    override val entityPerRegion: MetricRecorder[Long] with AbstractTestProbeWrapper = RecorderTestProbeWrapper(
      entityPerRegionProbe
    )

    override val shardRegionsOnNode: MetricRecorder[Long] with AbstractTestProbeWrapper = RecorderTestProbeWrapper(
      shardRegionsOnNodeProbe
    )

    override val reachableNodes: Counter[Long] with AbstractTestProbeWrapper = CounterTestProbeWrapper(
      reachableNodesProbe
    )

    override val unreachableNodes: Counter[Long] with AbstractTestProbeWrapper = CounterTestProbeWrapper(
      unreachableNodesProbe
    )

    override val nodeDown: UpCounter[Long] with AbstractTestProbeWrapper = CounterTestProbeWrapper(nodeDownProbe)
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
