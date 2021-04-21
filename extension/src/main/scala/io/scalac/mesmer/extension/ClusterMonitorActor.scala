package io.scalac.mesmer.extension

import akka.actor.typed.Behavior

import io.scalac.mesmer.extension.metric.ClusterMetricsMonitor

trait ClusterMonitorActor {
  def apply(monitor: ClusterMetricsMonitor): Behavior[_]
}
