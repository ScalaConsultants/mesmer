package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model._

object ActorMetricMonitor {
  final case class Labels(actorPath: ActorPath, node: Option[Node] = None, tags: Set[Tag] = Set.empty)
      extends LabelSerializable {
    override val serialize: RawLabels = node.serialize ++ actorPath.serialize ++ tags.flatMap(_.serialize)
  }

  trait BoundMonitor extends Synchronized with Bound {
    def mailboxSize: MetricObserver[Long]
    // TODO Create an abstraction to aggregate multiple metrics (e.g: mailboxTimeAgg: MetricObserverAgg[Long])
    def mailboxTimeAvg: MetricObserver[Long]
    def mailboxTimeMin: MetricObserver[Long]
    def mailboxTimeMax: MetricObserver[Long]
    def mailboxTimeSum: MetricObserver[Long]
    def stashSize: MetricRecorder[Long]
    def receivedMessages: MetricObserver[Long]
    def processedMessages: MetricObserver[Long]
    def failedMessages: MetricObserver[Long]
    def processingTimeAvg: MetricObserver[Long]
    def processingTimeMin: MetricObserver[Long]
    def processingTimeMax: MetricObserver[Long]
    def processingTimeSum: MetricObserver[Long]
    def sentMessages: MetricObserver[Long]
  }
}
