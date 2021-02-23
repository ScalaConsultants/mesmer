package io.scalac.extension

import akka.cluster.UniqueAddress

package object model {
  type Node          = String
  type Path          = String
  type Method        = String
  type ActorKey      = String
  type PersistenceId = String

  sealed trait Direction {
    import Direction._
    def serialize: Seq[String] = this match {
      case Out => Seq("direction", "out")
      case In  => Seq("direction", "in")
    }
  }

  object Direction {
    case object Out extends Direction
    case object In  extends Direction
  }

  implicit class AkkaNodeOps(val value: UniqueAddress) extends AnyVal {
    def toNode: Node = value.address.toString // @todo change to some meaningful name
  }
}
