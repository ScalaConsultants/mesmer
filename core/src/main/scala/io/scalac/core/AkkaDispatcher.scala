package io.scalac.core

import akka.actor.typed.DispatcherSelector

object AkkaDispatcher {
  val dispatcherSelector = DispatcherSelector.fromConfig("extension-dispatcher")
}
