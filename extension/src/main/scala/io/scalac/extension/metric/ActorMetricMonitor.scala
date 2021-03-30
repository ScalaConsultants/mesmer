package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model._

object ActorMetricMonitor {
  final case class Labels(actorPath: ActorPath, node: Option[Node] = None, tags: Set[Tag] = Set.empty)
      extends LabelSerializable {
    override val serialize: RawLabels = node.serialize ++ actorPath.serialize ++ tags.flatMap(_.serialize)
  }

  trait BoundMonitor extends Bound {
    def mailboxSize: MetricObserver[Long, Labels]
    // TODO Create an abstraction to aggregate multiple metrics (e.g: mailboxTimeAgg: MetricObserverAgg[Long])
    def mailboxTimeAvg: MetricObserver[Long, Labels]
    def mailboxTimeMin: MetricObserver[Long, Labels]
    def mailboxTimeMax: MetricObserver[Long, Labels]
    def mailboxTimeSum: MetricObserver[Long, Labels]
    def stashSize(labels: Labels): MetricRecorder[Long] // TODO stash should be MetricObserver too
    def receivedMessages: MetricObserver[Long, Labels]
    def processedMessages: MetricObserver[Long, Labels]
    def failedMessages: MetricObserver[Long, Labels]
    def processingTimeAvg: MetricObserver[Long, Labels]
    def processingTimeMin: MetricObserver[Long, Labels]
    def processingTimeMax: MetricObserver[Long, Labels]
    def processingTimeSum: MetricObserver[Long, Labels]
    def sentMessages: MetricObserver[Long, Labels]

  }
}
