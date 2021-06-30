package io.scalac.mesmer.core

import io.scalac.mesmer.core.model.{ ActorPath, SupportedVersion }

import scala.math.PartialOrdering

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

  val version26x = SupportedVersion.majors("2").and(SupportedVersion.minors("6"))

  val version101x = SupportedVersion.majors("10").and(SupportedVersion.minors("1"))

  val version102x = SupportedVersion.majors("10").and(SupportedVersion.minors("2"))

}
