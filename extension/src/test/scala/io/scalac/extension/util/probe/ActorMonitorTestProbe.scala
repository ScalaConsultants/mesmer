package io.scalac.extension.util.probe

import scala.collection.mutable

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.extension.metric.{ ActorMetricMonitor, MetricRecorder }
import io.scalac.extension.util.TestProbeSynchronized
import io.scalac.extension.util.probe.BoundTestProbe.MetricRecorderCommand

class ActorMonitorTestProbe(implicit actorSystem: ActorSystem[_]) extends ActorMetricMonitor {

  import ActorMetricMonitor._

  private val bindsMap = mutable.HashMap.empty[Labels, TestBoundMonitor]

  override def bind(labels: Labels): TestBoundMonitor = synchronized {
    bindsMap.getOrElseUpdate(labels, new TestBoundMonitor(TestProbe()))
  }

  class TestBoundMonitor(val mailboxSizeProbe: TestProbe[MetricRecorderCommand])
      extends BoundMonitor
      with TestProbeSynchronized {
    override val mailboxSize: MetricRecorder[Long] with AbstractTestProbeWrapper =
      RecorderTestProbeWrapper(mailboxSizeProbe)
    override def unbind(): Unit = ()
  }

}
