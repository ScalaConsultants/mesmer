package io.scalac.extension.metric

import io.scalac.core.model.Tag
import io.scalac.extension.model.{ Node, Path }

object ActorMetricMonitor {
  case class Labels(actorPath: Path, node: Option[Node] = None, tags: Set[Tag] = Set.empty)
  trait BoundMonitor extends Synchronized with Bound {
    def mailboxSize: MetricObserver[Long]
    // TODO Create an abstraction to aggregate multiple metrics (e.g: mailboxTimeAgg: MetricObserverAgg[Long])
    def mailboxTimeAvg: MetricObserver[Long]
    def mailboxTimeMin: MetricObserver[Long]
    def mailboxTimeMax: MetricObserver[Long]
    def mailboxTimeSum: MetricObserver[Long]
    def stashSize: MetricRecorder[Long] with Instrument[Long]
    def receivedMessages: MetricObserver[Long]
    def processedMessages: MetricObserver[Long]
    def failedMessages: MetricObserver[Long]
  }
}
