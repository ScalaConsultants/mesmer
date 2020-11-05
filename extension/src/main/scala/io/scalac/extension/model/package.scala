package io.scalac.extension

import akka.cluster.UniqueAddress

package object model {
  type Node = String

  implicit class AkkaNodeOps(val value: UniqueAddress) extends AnyVal {
    def toNode: Node = value.toString // @todo change to some meaningful name
  }
}
