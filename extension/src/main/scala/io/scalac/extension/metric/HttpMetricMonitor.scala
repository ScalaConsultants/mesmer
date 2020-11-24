package io.scalac.extension.metric

import io.scalac.extension.model._

object HttpMetricMonitor {
  trait BoundMonitor {
    def requestTime: MetricRecorder[Long]
    def requestCounter: UpCounter[Long]
  }
}
trait HttpMetricMonitor {
  import HttpMetricMonitor._
  def bind(node: Path, method: Method): BoundMonitor
}
