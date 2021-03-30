package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.StreamMetricMonitor.{ BoundMonitor, Labels }
import io.scalac.extension.metric.{ StreamMetricMonitor, StreamOperatorMetricsMonitor }
import io.scalac.extension.util.probe.BoundTestProbe.{ MetricObserverCommand, MetricRecorderCommand }

final case class StreamOperatorMonitorTestProbe(
  processedTestProbe: TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Labels]],
  runningOperatorsTestProbe: TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Labels]],
  demandTestProbe: TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Labels]],
  collector: ObserverCollector
)(implicit val system: ActorSystem[_])
    extends StreamOperatorMetricsMonitor
    with Collected {

  override def bind(): StreamOperatorMetricsMonitor.BoundMonitor = new StreamOperatorMetricsMonitor.BoundMonitor {
    val processedMessages = ObserverTestProbeWrapper(processedTestProbe, collector)
    val operators         = ObserverTestProbeWrapper(runningOperatorsTestProbe, collector)
    val demand            = ObserverTestProbeWrapper(demandTestProbe, collector)
    def unbind(): Unit    = ()
  }
}

object StreamOperatorMonitorTestProbe {
  def apply(collector: ObserverCollector)(implicit system: ActorSystem[_]): StreamOperatorMonitorTestProbe = {
    val processProbe =
      TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Labels]]("akka_stream_processed_messages")
    val demandProbe =
      TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Labels]]("akka_stream_demand")
    val runningOperators =
      TestProbe[MetricObserverCommand[StreamOperatorMetricsMonitor.Labels]]("akka_stream_running_operators")

    StreamOperatorMonitorTestProbe(processProbe, demandProbe, runningOperators, collector)
  }
}

class StreamMonitorTestProbe(
  val runningStreamsProbe: TestProbe[MetricRecorderCommand],
  val streamActorsProbe: TestProbe[MetricRecorderCommand],
  val processedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  val collector: ObserverCollector
)(implicit val system: ActorSystem[_])
    extends StreamMetricMonitor
    with Collected {
  override def bind(labels: StreamMetricMonitor.EagerLabels): StreamMetricMonitor.BoundMonitor = new BoundMonitor {

    val runningStreamsTotal = RecorderTestProbeWrapper(runningStreamsProbe)

    val streamActorsTotal = RecorderTestProbeWrapper(streamActorsProbe)

    val streamProcessedMessages = ObserverTestProbeWrapper(processedMessagesProbe, collector)

    def unbind(): Unit = ()
  }
}

object StreamMonitorTestProbe {
  def apply(collector: ObserverCollector)(implicit system: ActorSystem[_]): StreamMonitorTestProbe = {
    val runningStream         = TestProbe[MetricRecorderCommand]()
    val streamActorsProbe     = TestProbe[MetricRecorderCommand]()
    val procesedMessagesProbe = TestProbe[MetricObserverCommand[Labels]]()
    new StreamMonitorTestProbe(runningStream, streamActorsProbe, procesedMessagesProbe, collector)
  }
}
