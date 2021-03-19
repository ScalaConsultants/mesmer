package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.{ ActorMetricMonitor, MetricObserver, MetricRecorder }
import io.scalac.extension.util.probe.BoundTestProbe.{ MetricObserverCommand, MetricRecorderCommand }

import scala.collection.mutable

class ActorMonitorTestProbe(collector: ObserverCollector)(implicit val actorSystem: ActorSystem[_])
    extends ActorMetricMonitor {

  import ActorMonitorTestProbe._

  private val binds = mutable.ListBuffer.empty[TestBoundMonitor]

  override def bind(): TestBoundMonitor = {
    lazy val monitor: TestBoundMonitor = TestBoundMonitor(
      TestProbe("mailbox-size-probe"),
      TestProbe("mailbox-time-avg-probe"),
      TestProbe("mailbox-time-min-probe"),
      TestProbe("mailbox-time-max-probe"),
      TestProbe("mailbox-time-sum-probe"),
      TestProbe("stash-size-probe"),
      TestProbe("received-messages-probe"),
      TestProbe("processed-messages-probe"),
      TestProbe("failed-messages-probe"),
      collector,
      () => binds.filterInPlace(_ != monitor)
    )
    monitor
  }

}

object ActorMonitorTestProbe {
  import ActorMetricMonitor._
  case class TestBoundMonitor(
    mailboxSizeProbe: TestProbe[MetricObserverCommand[Labels]],
    mailboxTimeAvgProbe: TestProbe[MetricObserverCommand[Labels]],
    mailboxTimeMinProbe: TestProbe[MetricObserverCommand[Labels]],
    mailboxTimeMaxProbe: TestProbe[MetricObserverCommand[Labels]],
    mailboxTimeSumProbe: TestProbe[MetricObserverCommand[Labels]],
    stashSizeProbe: TestProbe[MetricRecorderCommand],
    receivedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
    processedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
    failedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
    collector: ObserverCollector,
    onUnbind: () => Unit
  )(implicit actorSystem: ActorSystem[_])
      extends BoundMonitor {
    val mailboxSize: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxSizeProbe, collector)
    val mailboxTimeAvg: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxTimeAvgProbe, collector)
    val mailboxTimeMin: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxTimeMinProbe, collector)
    val mailboxTimeMax: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxTimeMaxProbe, collector)
    val mailboxTimeSum: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxTimeSumProbe, collector)
    val receivedMessages: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(receivedMessagesProbe, collector)
    val processedMessages: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(processedMessagesProbe, collector)
    val failedMessages: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(failedMessagesProbe, collector)

    override def unbind(): Unit = {
      collector.finish(mailboxSizeProbe)
      collector.finish(mailboxTimeAvgProbe)
      collector.finish(mailboxTimeMinProbe)
      collector.finish(mailboxTimeMaxProbe)
      collector.finish(mailboxTimeSumProbe)
      collector.finish(receivedMessagesProbe)
      collector.finish(processedMessagesProbe)
      collector.finish(failedMessagesProbe)
    }

    override def stashSize(labels: Labels): MetricRecorder[Long] = RecorderTestProbeWrapper(stashSizeProbe)
  }
}
