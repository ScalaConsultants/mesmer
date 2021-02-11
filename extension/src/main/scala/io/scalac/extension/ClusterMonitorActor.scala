package io.scalac.extension

import akka.actor.typed.Behavior

import io.scalac.extension.metric.ClusterMetricsMonitor

trait ClusterMonitorActor {
  def apply(monitor: ClusterMetricsMonitor): Behavior[_]
}
