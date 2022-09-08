package io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension

import akka.actor.typed.Behavior

trait ClusterMonitorActor {

  def apply(): Behavior[_]
}
