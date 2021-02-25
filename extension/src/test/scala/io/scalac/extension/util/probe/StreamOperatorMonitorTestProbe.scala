package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.StreamMetricMonitor.BoundMonitor
import io.scalac.extension.metric.{ MetricRecorder, StreamMetricMonitor, StreamOperatorMetricsMonitor }
import io.scalac.extension.util.probe.BoundTestProbe.{ LazyMetricsObserved, MetricRecorderCommand }

import scala.concurrent.duration.FiniteDuration

class StreamOperatorMonitorTestProbe(
  val processedTestProbe: TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]],
  val runningOperators: TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]],
  ping: FiniteDuration
)(implicit val system: ActorSystem[_])
    extends StreamOperatorMetricsMonitor {

  override def processedMessages = LazyObserverTestProbeWrapper(processedTestProbe, ping)

  override def operators = LazyObserverTestProbeWrapper(runningOperators, ping)
}

object StreamOperatorMonitorTestProbe {
  def apply(ping: FiniteDuration)(implicit system: ActorSystem[_]): StreamOperatorMonitorTestProbe = {
    val processProbe =
      TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]]("akka_stream_processed_messages")
    val runningOperators =
      TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]]("akka_stream_running_operators")

    new StreamOperatorMonitorTestProbe(processProbe, runningOperators, ping)
  }
}

class StreamMonitorTestProbe(
  val runningStreamsProbe: TestProbe[MetricRecorderCommand],
  val streamActorsProbe: TestProbe[MetricRecorderCommand]
) extends StreamMetricMonitor {
  override def bind(labels: StreamMetricMonitor.Labels): StreamMetricMonitor.BoundMonitor = new BoundMonitor {
    override def runningStreams: MetricRecorder[Long] = RecorderTestProbeWrapper(runningStreamsProbe)

    override def streamActors: MetricRecorder[Long] = RecorderTestProbeWrapper(streamActorsProbe)

    override def unbind(): Unit = ()
  }
}

object StreamMonitorTestProbe {
  def apply()(implicit system: ActorSystem[_]): StreamMonitorTestProbe = {
    val runningStream     = TestProbe[MetricRecorderCommand]
    val streamActorsProbe = TestProbe[MetricRecorderCommand]
    new StreamMonitorTestProbe(runningStream, streamActorsProbe)
  }
}
