package io.scalac.extension.metric
import io.scalac.extension.model._

object PersistenceMetricMonitor {
  trait BoundMonitor {
    def recoveryTime: MetricRecorder[Long]
  }
}

trait PersistenceMetricMonitor extends Bindable[Path] {
  import PersistenceMetricMonitor._

  override type Bound = BoundMonitor
}
