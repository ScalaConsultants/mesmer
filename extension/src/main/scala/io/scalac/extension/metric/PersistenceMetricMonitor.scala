package io.scalac.extension.metric
import io.scalac.extension.model._

object PersistenceMetricMonitor {
  trait BoundMonitor {
    def recoveryTime: MetricRecorder[Long]
  }
}

trait PersistenceMetricMonitor {
  import PersistenceMetricMonitor._

  def bind(node: Node, path: Path): BoundMonitor


}
