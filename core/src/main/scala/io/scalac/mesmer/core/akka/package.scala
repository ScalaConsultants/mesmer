package io.scalac.mesmer.core

import scala.math.PartialOrdering

import io.scalac.mesmer.core.model.ActorPath

package object akka {

  implicit val actorPathPartialOrdering: PartialOrdering[ActorPath] = new PartialOrdering[ActorPath] {
    def tryCompare(x: ActorPath, y: ActorPath): Option[Int] =
      (x, y) match {
        case (xPath, yPath) if xPath == yPath          => Some(0)
        case (xPath, yPath) if xPath.startsWith(yPath) => Some(1)
        case (xPath, yPath) if yPath.startsWith(xPath) => Some(-1)
        case _                                         => None
      }

    def lteq(x: ActorPath, y: ActorPath): Boolean = actorLevel(x) <= actorLevel(y)

    private def actorLevel(path: ActorPath): Int = path.count(_ == '/')
  }

}
