package io.scalac.mesmer.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.mesmer.core.util.probe.Collected
import io.scalac.mesmer.core.util.probe.ObserverCollector
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor._
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.MetricObserverCommand

final case class ActorMonitorTestProbe(
  mailboxSizeProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeMinProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeMaxProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeSumProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeCountProbe: TestProbe[MetricObserverCommand[Labels]],
  stashedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  receivedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  processedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  failedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  processingTimeMinProbe: TestProbe[MetricObserverCommand[Labels]],
  processingTimeMaxProbe: TestProbe[MetricObserverCommand[Labels]],
  processingTimeSumProbe: TestProbe[MetricObserverCommand[Labels]],
  processingTimeCountProbe: TestProbe[MetricObserverCommand[Labels]],
  sentMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  droppedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  collector: ObserverCollector
)(implicit val actorSystem: ActorSystem[_])
    extends ActorMetricsMonitor
    with BindUnbindMonitor
    with Collected {

  def bind(): ActorMonitorTestBoundMonitor with UnbindMonitor = {
    onBind()
    new ActorMonitorTestBoundMonitor with UnbindMonitor
  }

  class ActorMonitorTestBoundMonitor extends BoundMonitor {
    val mailboxSize: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxSizeProbe, collector)
    val mailboxTimeCount: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxTimeCountProbe, collector)
    val mailboxTimeMin: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxTimeMinProbe, collector)
    val mailboxTimeMax: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxTimeMaxProbe, collector)
    val mailboxTimeSum: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(mailboxTimeSumProbe, collector)
    val stashedMessages: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(stashedMessagesProbe, collector)
    val receivedMessages: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(receivedMessagesProbe, collector)
    val processedMessages: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(processedMessagesProbe, collector)
    val failedMessages: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(failedMessagesProbe, collector)
    val processingTimeCount: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(processingTimeCountProbe, collector)
    val processingTimeMin: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(processingTimeMinProbe, collector)
    val processingTimeMax: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(processingTimeMaxProbe, collector)
    val processingTimeSum: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(processingTimeSumProbe, collector)
    val sentMessages: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(sentMessagesProbe, collector)

    val droppedMessages: MetricObserver[Long, Labels] =
      ObserverTestProbeWrapper(droppedMessagesProbe, collector)

    def unbind(): Unit = {
      collector.finish(mailboxSizeProbe)
      collector.finish(mailboxTimeCountProbe)
      collector.finish(mailboxTimeMinProbe)
      collector.finish(mailboxTimeMaxProbe)
      collector.finish(mailboxTimeSumProbe)
      collector.finish(receivedMessagesProbe)
      collector.finish(processedMessagesProbe)
      collector.finish(failedMessagesProbe)
      collector.finish(processingTimeCountProbe)
      collector.finish(processingTimeMinProbe)
      collector.finish(processingTimeMaxProbe)
      collector.finish(processingTimeSumProbe)
      collector.finish(sentMessagesProbe)
      collector.finish(droppedMessagesProbe)
    }
  }
}

object ActorMonitorTestProbe {

  def apply(collector: ObserverCollector)(implicit actorSystem: ActorSystem[_]): ActorMonitorTestProbe =
    ActorMonitorTestProbe(
      TestProbe("mailbox-size-probe"),
      TestProbe("mailbox-time-min-probe"),
      TestProbe("mailbox-time-max-probe"),
      TestProbe("mailbox-time-sum-probe"),
      TestProbe("mailbox-time-count-probe"),
      TestProbe("stash-size-probe"),
      TestProbe("received-messages-probe"),
      TestProbe("processed-messages-probe"),
      TestProbe("failed-messages-probe"),
      TestProbe("processing-time-min-probe"),
      TestProbe("processing-time-max-probe"),
      TestProbe("processing-time-sum-probe"),
      TestProbe("processing-time-count-probe"),
      TestProbe("sent-messages-probe"),
      TestProbe("dropped-messages-probe"),
      collector
    )

}
