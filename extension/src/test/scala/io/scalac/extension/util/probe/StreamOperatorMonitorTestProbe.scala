package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.StreamMetricMonitor.BoundMonitor
import io.scalac.extension.metric.{ MetricObserver, StreamMetricMonitor, StreamOperatorMetricsMonitor }
import io.scalac.extension.util.probe.BoundTestProbe.{ LazyMetricsObserved, MetricObserverCommand }

import scala.concurrent.duration.FiniteDuration

class StreamOperatorMonitorTestProbe(
  val processedTestProbe: TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]],
  val runningOperatorsTestProbe: TestProbe[LazyMetricsObserved[StreamOperatorMetricsMonitor.Labels]],
  ping: FiniteDuration
)(implicit val system: ActorSystem[_])
    extends StreamOperatorMetricsMonitor {

  override def bind(): StreamOperatorMetricsMonitor.BoundMonitor = new StreamOperatorMetricsMonitor.BoundMonitor {
    override def processedMessages = LazyObserverTestProbeWrapper(processedTestProbe, ping)

    override def operators = LazyObserverTestProbeWrapper(runningOperatorsTestProbe, ping)

    override def unbind(): Unit = ()
  }
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
  val runningStreamsProbe: TestProbe[MetricObserverCommand],
  val streamActorsProbe: TestProbe[MetricObserverCommand],
  ping: FiniteDuration
)(implicit val system: ActorSystem[_])
    extends StreamMetricMonitor {
  override def bind(labels: StreamMetricMonitor.Labels): StreamMetricMonitor.BoundMonitor = new BoundMonitor {
    override def runningStreams: MetricObserver[Long] = ObserverTestProbeWrapper(runningStreamsProbe, ping)

    override def streamActors: MetricObserver[Long] = ObserverTestProbeWrapper(streamActorsProbe, ping)

    override def unbind(): Unit = ()
  }
}

object StreamMonitorTestProbe {
  def apply(ping: FiniteDuration)(implicit system: ActorSystem[_]): StreamMonitorTestProbe = {
    val runningStream     = TestProbe[MetricObserverCommand]
    val streamActorsProbe = TestProbe[MetricObserverCommand]
    new StreamMonitorTestProbe(runningStream, streamActorsProbe, ping)
  }
}
