package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.ActorMetricMonitor._
import io.scalac.extension.metric.{ ActorMetricMonitor, MetricObserver, MetricRecorder }
import io.scalac.extension.util.probe.BoundTestProbe.{ MetricObserverCommand, MetricRecorderCommand }

final case class ActorMonitorTestProbe(
  mailboxSizeProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeAvgProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeMinProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeMaxProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeSumProbe: TestProbe[MetricObserverCommand[Labels]],
  stashSizeProbe: TestProbe[MetricRecorderCommand],
  receivedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  processedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  failedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  collector: ObserverCollector
)(implicit val actorSystem: ActorSystem[_])
    extends ActorMetricMonitor {

  override def bind(): BoundMonitor = new BoundMonitor {
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
    override def stashSize(labels: Labels): MetricRecorder[Long] = RecorderTestProbeWrapper(stashSizeProbe)
    def unbind(): Unit = {
      collector.finish(mailboxSizeProbe)
      collector.finish(mailboxTimeAvgProbe)
      collector.finish(mailboxTimeMinProbe)
      collector.finish(mailboxTimeMaxProbe)
      collector.finish(mailboxTimeSumProbe)
      collector.finish(receivedMessagesProbe)
      collector.finish(processedMessagesProbe)
      collector.finish(failedMessagesProbe)
    }

  }
}

object ActorMonitorTestProbe {

  def apply(collector: ObserverCollector)(implicit system: ActorSystem[_]): ActorMonitorTestProbe =
    ActorMonitorTestProbe(
      TestProbe("mailbox-size-probe"),
      TestProbe("mailbox-time-avg-probe"),
      TestProbe("mailbox-time-min-probe"),
      TestProbe("mailbox-time-max-probe"),
      TestProbe("mailbox-time-sum-probe"),
      TestProbe("stash-size-probe"),
      TestProbe("received-messages-probe"),
      TestProbe("processed-messages-probe"),
      TestProbe("failed-messages-probe"),
      collector
    )
}
