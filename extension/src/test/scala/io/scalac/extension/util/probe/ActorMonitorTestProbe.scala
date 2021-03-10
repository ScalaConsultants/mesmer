package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.{ ActorMetricMonitor, MetricObserver, MetricRecorder }
import io.scalac.extension.util.TestProbeSynchronized
import io.scalac.extension.util.probe.BoundTestProbe.{ MetricObserverCommand, MetricRecorderCommand }

import scala.collection.mutable

class ActorMonitorTestProbe(collector: ObserverCollector)(implicit val actorSystem: ActorSystem[_])
    extends ActorMetricMonitor {

  import ActorMetricMonitor._
  import ActorMonitorTestProbe._

  private val bindsMap = mutable.HashMap.empty[Labels, TestBoundMonitor]

  override def bind(labels: Labels): TestBoundMonitor =
    bindsMap.getOrElseUpdate(
      labels,
      new TestBoundMonitor(
        TestProbe("mailbox-size-probe"),
        TestProbe("mailbox-time-avg-probe"),
        TestProbe("mailbox-time-min-probe"),
        TestProbe("mailbox-time-max-probe"),
        TestProbe("mailbox-time-sum-probe"),
        TestProbe("stash-size-probe"),
        collector,
        () => bindsMap.remove(labels)
      )
    )

}

object ActorMonitorTestProbe {
  import ActorMetricMonitor._
  class TestBoundMonitor(
    val mailboxSizeProbe: TestProbe[MetricObserverCommand],
    val mailboxTimeAvgProbe: TestProbe[MetricObserverCommand],
    val mailboxTimeMinProbe: TestProbe[MetricObserverCommand],
    val mailboxTimeMaxProbe: TestProbe[MetricObserverCommand],
    val mailboxTimeSumProbe: TestProbe[MetricObserverCommand],
    val stashSizeProbe: TestProbe[MetricRecorderCommand],
    collector: ObserverCollector,
    onUnbind: () => Unit
  )(implicit actorSystem: ActorSystem[_])
      extends BoundMonitor
      with TestProbeSynchronized {
    val mailboxSize: MetricObserver[Long] =
      ObserverTestProbeWrapper(mailboxSizeProbe, collector)
    val mailboxTimeAvg: MetricObserver[Long] =
      ObserverTestProbeWrapper(mailboxTimeAvgProbe, collector)
    val mailboxTimeMin: MetricObserver[Long] =
      ObserverTestProbeWrapper(mailboxTimeMinProbe, collector)
    val mailboxTimeMax: MetricObserver[Long] =
      ObserverTestProbeWrapper(mailboxTimeMaxProbe, collector)
    val mailboxTimeSum: MetricObserver[Long] =
      ObserverTestProbeWrapper(mailboxTimeSumProbe, collector)
    val stashSize: MetricRecorder[Long] with SyncTestProbeWrapper =
      RecorderTestProbeWrapper(stashSizeProbe)
    override def unbind(): Unit = {
      collector.finish(mailboxSizeProbe)
      collector.finish(mailboxTimeAvgProbe)
      collector.finish(mailboxTimeMinProbe)
      collector.finish(mailboxTimeMaxProbe)
      collector.finish(mailboxTimeSumProbe)
      onUnbind()
    }
  }
}
