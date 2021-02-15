package io.scalac.extension.util.probe

import scala.collection.mutable

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.extension.metric.ActorMetricMonitor.BoundMonitor
import io.scalac.extension.metric.{ ActorMetricMonitor, MetricObserver, MetricRecorder }
import io.scalac.extension.util.TestProbeSynchronized
import io.scalac.extension.util.probe.BoundTestProbe.{ MetricObserverCommand, MetricRecorderCommand }

class ActorMonitorTestProbe(implicit val actorSystem: ActorSystem[_]) extends ActorMetricMonitor {

  import ActorMetricMonitor._
  import ActorMonitorTestProbe._

  private val bindsMap = mutable.HashMap.empty[Labels, TestBoundMonitor]

  override def bind(labels: Labels): TestBoundMonitor = synchronized {
    bindsMap.getOrElseUpdate(labels, new TestBoundMonitor(TestProbe(), TestProbe()))
  }

}

object ActorMonitorTestProbe {
  class TestBoundMonitor(
    val mailboxSizeProbe: TestProbe[MetricObserverCommand],
    val stashSizeProbe: TestProbe[MetricRecorderCommand]
  )(implicit actorSystem: ActorSystem[_])
      extends BoundMonitor
      with TestProbeSynchronized {
    override val mailboxSize: MetricObserver[Long] with AbstractTestProbeWrapper =
      ObserverTestProbeWrapper(mailboxSizeProbe)
    override val stashSize: MetricRecorder[Long] with AbstractTestProbeWrapper =
      RecorderTestProbeWrapper(stashSizeProbe)
    override def unbind(): Unit = ()
  }
}
