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
        TestProbe("received-messages-probe"),
        TestProbe("processed-messages-probe"),
        TestProbe("failed-messages-probe"),
        TestProbe("processing-time-avg-probe"),
        TestProbe("processing-time-min-probe"),
        TestProbe("processing-time-max-probe"),
        TestProbe("processing-time-sum-probe"),
        TestProbe("sent-messages-probe"),
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
    val receivedMessagesProbe: TestProbe[MetricObserverCommand],
    val processedMessagesProbe: TestProbe[MetricObserverCommand],
    val failedMessagesProbe: TestProbe[MetricObserverCommand],
    val processingTimeAvgProbe: TestProbe[MetricObserverCommand],
    val processingTimeMinProbe: TestProbe[MetricObserverCommand],
    val processingTimeMaxProbe: TestProbe[MetricObserverCommand],
    val processingTimeSumProbe: TestProbe[MetricObserverCommand],
    val sentMessagesProbe: TestProbe[MetricObserverCommand],
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
    val receivedMessages: MetricObserver[Long] =
      ObserverTestProbeWrapper(receivedMessagesProbe, collector)
    val processedMessages: MetricObserver[Long] =
      ObserverTestProbeWrapper(processedMessagesProbe, collector)
    val failedMessages: MetricObserver[Long] =
      ObserverTestProbeWrapper(failedMessagesProbe, collector)
    val processingTimeAvg: MetricObserver[Long] =
      ObserverTestProbeWrapper(processingTimeAvgProbe, collector)
    val processingTimeMin: MetricObserver[Long] =
      ObserverTestProbeWrapper(processingTimeMinProbe, collector)
    val processingTimeMax: MetricObserver[Long] =
      ObserverTestProbeWrapper(processingTimeMaxProbe, collector)
    val processingTimeSum: MetricObserver[Long] =
      ObserverTestProbeWrapper(processingTimeSumProbe, collector)
    val sentMessages: MetricObserver[Long] =
      ObserverTestProbeWrapper(sentMessagesProbe, collector)
    override def unbind(): Unit = {
      collector.finish(mailboxSizeProbe)
      collector.finish(mailboxTimeAvgProbe)
      collector.finish(mailboxTimeMinProbe)
      collector.finish(mailboxTimeMaxProbe)
      collector.finish(mailboxTimeSumProbe)
      collector.finish(receivedMessagesProbe)
      collector.finish(processedMessagesProbe)
      collector.finish(failedMessagesProbe)
      collector.finish(processingTimeAvgProbe)
      collector.finish(processingTimeMinProbe)
      collector.finish(processingTimeMaxProbe)
      collector.finish(processingTimeSumProbe)
      collector.finish(sentMessagesProbe)
      onUnbind()
    }
  }
}
