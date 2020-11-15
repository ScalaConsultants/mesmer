package io.scalac.extension

import akka.cluster.UniqueAddress

package object model {
  type Node = String
  type Path = String

  implicit class AkkaNodeOps(val value: UniqueAddress) extends AnyVal {
    def toNode: Node = value.address.toString // @todo change to some meaningful name
  }
}
