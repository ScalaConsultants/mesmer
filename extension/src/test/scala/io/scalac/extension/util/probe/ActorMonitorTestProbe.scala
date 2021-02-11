package io.scalac.extension.util.probe

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.extension.metric.{ ActorMetricMonitor, MetricRecorder }
import io.scalac.extension.util.{ TestProbeAsynchronized, TestProbeSynchronized }
import io.scalac.extension.util.probe.BoundTestProbe.MetricRecorderCommand

class ActorMonitorTestProbe(val pingOffset: FiniteDuration)(implicit val actorSystem: ActorSystem[_])
    extends ActorMetricMonitor
    with TestProbeAsynchronized {

  import ActorMetricMonitor._

  private val bindsMap = mutable.HashMap.empty[Labels, TestBoundMonitor]

  override def bind(labels: Labels): TestBoundMonitor = synchronized {
    bindsMap.getOrElseUpdate(labels, new TestBoundMonitor(TestProbe(), TestProbe()))
  }

  class TestBoundMonitor(
    val mailboxSizeProbe: TestProbe[MetricRecorderCommand],
    val stashSizeProbe: TestProbe[MetricRecorderCommand]
  ) extends BoundMonitor
      with TestProbeSynchronized {

    override val mailboxSize: MetricRecorder[Long] with AbstractTestProbeWrapper =
      RecorderTestProbeWrapper(mailboxSizeProbe)

    override val stashSize: MetricRecorder[Long] with AbstractTestProbeWrapper =
      RecorderTestProbeWrapper(stashSizeProbe)

    override def unbind(): Unit = ()
  }

}
