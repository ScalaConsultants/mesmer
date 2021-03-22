package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.StreamMetricMonitor.{ BoundMonitor, Labels }
import io.scalac.extension.metric.{
  LazyMetricObserver,
  MetricRecorder,
  StreamMetricMonitor,
  StreamOperatorMetricsMonitor
}
import io.scalac.extension.util.probe.BoundTestProbe.{ LazyMetricsObserved, MetricRecorderCommand }

class StreamOperatorMonitorTestProbe(
  val processedTestProbe: TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]],
  val demandTestProbe: TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]],
  val runningOperatorsTestProbe: TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]],
  collector: ObserverCollector
)(implicit val system: ActorSystem[_])
    extends StreamOperatorMetricsMonitor {

  override def bind(): StreamOperatorMetricsMonitor.BoundMonitor = new StreamOperatorMetricsMonitor.BoundMonitor {

    override def processedMessages = LazyObserverTestProbeWrapper(processedTestProbe, collector)
    override def demand            = LazyObserverTestProbeWrapper(demandTestProbe, collector)
    override def operators         = LazyObserverTestProbeWrapper(runningOperatorsTestProbe, collector)
    override def unbind(): Unit    = ()
  }
}

object StreamOperatorMonitorTestProbe {
  def apply(collector: ObserverCollector)(implicit system: ActorSystem[_]): StreamOperatorMonitorTestProbe = {
    val processProbe =
      TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]]("akka_stream_processed_messages")
    val demandProbe =
      TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]]("akka_stream_operator_demand")
    val runningOperators =
      TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]]("akka_stream_running_operators")

    new StreamOperatorMonitorTestProbe(processProbe, demandProbe, runningOperators, collector)
  }
}

class StreamMonitorTestProbe(
  val runningStreamsProbe: TestProbe[MetricRecorderCommand],
  val streamActorsProbe: TestProbe[MetricRecorderCommand],
  val processedMessagesProbe: TestProbe[LazyMetricsObserved[Labels]],
  collector: ObserverCollector
)(implicit val system: ActorSystem[_])
    extends StreamMetricMonitor {
  override def bind(labels: StreamMetricMonitor.EagerLabels): StreamMetricMonitor.BoundMonitor = new BoundMonitor {

    override def runningStreamsTotal = RecorderTestProbeWrapper(runningStreamsProbe)

    override def streamActorsTotal: MetricRecorder[Long] = RecorderTestProbeWrapper(streamActorsProbe)

    override def streamProcessedMessages: LazyMetricObserver[Long, StreamMetricMonitor.Labels] =
      LazyObserverTestProbeWrapper(processedMessagesProbe, collector)

    override def unbind(): Unit = ()
  }
}

object StreamMonitorTestProbe {
  def apply(collector: ObserverCollector)(implicit system: ActorSystem[_]): StreamMonitorTestProbe = {
    val runningStream         = TestProbe[MetricRecorderCommand]
    val streamActorsProbe     = TestProbe[MetricRecorderCommand]
    val procesedMessagesProbe = TestProbe[LazyMetricsObserved[Labels]]
    new StreamMonitorTestProbe(runningStream, streamActorsProbe, procesedMessagesProbe, collector)
  }
}
