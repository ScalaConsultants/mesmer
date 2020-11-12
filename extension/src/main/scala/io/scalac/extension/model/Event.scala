package io.scalac.extension.model

import akka.cluster.UniqueAddress

sealed trait Event

object Event {

  case class ClusterChangedEvent(status: String, node: UniqueAddress) extends Event
}
