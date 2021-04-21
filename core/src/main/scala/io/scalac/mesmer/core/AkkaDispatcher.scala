package io.scalac.mesmer.core

import _root_.akka.actor.typed.ActorSystem
import _root_.akka.actor.typed.DispatcherSelector

object AkkaDispatcher {
  final val dispatcherConfig = "io.scalac.akka-monitoring.dispatcher"

  val dispatcherSelector: DispatcherSelector = DispatcherSelector
    .fromConfig(dispatcherConfig)

  def safeDispatcherSelector(implicit system: ActorSystem[_]): DispatcherSelector =
    if (system.settings.config.hasPath(dispatcherConfig)) dispatcherSelector else DispatcherSelector.default()
}
