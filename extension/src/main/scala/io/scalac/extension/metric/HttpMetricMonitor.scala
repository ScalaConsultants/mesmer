package io.scalac.extension.metric

import io.scalac.extension.model._

object HttpMetricMonitor {
  trait BoundMonitor {
    def requestTime: MetricRecorder[Long]
    def requestCounter: Counter[Long]
  }
}
trait HttpMetricMonitor {
  import HttpMetricMonitor._
  def bind(node: Node, path: Path, method: Method): BoundMonitor
}
