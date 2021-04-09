package io.scalac.core

import _root_.akka.actor.typed.DispatcherSelector

object AkkaDispatcher {
  val dispatcherSelector = DispatcherSelector.fromConfig("extension-dispatcher")
}
