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
  mailboxSizeProbe: TestProbe[MetricObserverCommand[Attributes]],
  mailboxTimeMinProbe: TestProbe[MetricObserverCommand[Attributes]],
  mailboxTimeMaxProbe: TestProbe[MetricObserverCommand[Attributes]],
  mailboxTimeSumProbe: TestProbe[MetricObserverCommand[Attributes]],
  mailboxTimeCountProbe: TestProbe[MetricObserverCommand[Attributes]],
  stashedMessagesProbe: TestProbe[MetricObserverCommand[Attributes]],
  receivedMessagesProbe: TestProbe[MetricObserverCommand[Attributes]],
  processedMessagesProbe: TestProbe[MetricObserverCommand[Attributes]],
  failedMessagesProbe: TestProbe[MetricObserverCommand[Attributes]],
  processingTimeMinProbe: TestProbe[MetricObserverCommand[Attributes]],
  processingTimeMaxProbe: TestProbe[MetricObserverCommand[Attributes]],
  processingTimeSumProbe: TestProbe[MetricObserverCommand[Attributes]],
  processingTimeCountProbe: TestProbe[MetricObserverCommand[Attributes]],
  sentMessagesProbe: TestProbe[MetricObserverCommand[Attributes]],
  droppedMessagesProbe: TestProbe[MetricObserverCommand[Attributes]],
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
    val mailboxSize: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(mailboxSizeProbe, collector)
    val mailboxTimeCount: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(mailboxTimeCountProbe, collector)
    val mailboxTimeMin: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(mailboxTimeMinProbe, collector)
    val mailboxTimeMax: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(mailboxTimeMaxProbe, collector)
    val mailboxTimeSum: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(mailboxTimeSumProbe, collector)
    val stashedMessages: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(stashedMessagesProbe, collector)
    val receivedMessages: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(receivedMessagesProbe, collector)
    val processedMessages: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(processedMessagesProbe, collector)
    val failedMessages: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(failedMessagesProbe, collector)
    val processingTimeCount: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(processingTimeCountProbe, collector)
    val processingTimeMin: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(processingTimeMinProbe, collector)
    val processingTimeMax: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(processingTimeMaxProbe, collector)
    val processingTimeSum: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(processingTimeSumProbe, collector)
    val sentMessages: MetricObserver[Long, Attributes] =
      ObserverTestProbeWrapper(sentMessagesProbe, collector)

    val droppedMessages: MetricObserver[Long, Attributes] =
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
