package io.scalac.extension.service

import io.scalac.core.model._
import io.scalac.core.tagging._

object CommonRegexPathService extends PathService {
  private val uuid   = """^[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}$""".r
  private val number = """^[+-]?\d+\.?\d*$""".r

  val numberTemplate         = "{num}"
  val uuidTemplate           = "{uuid}"
  private val detectionChain = List((number, numberTemplate), (uuid, uuidTemplate))

  override def template(path: Path): Path =
    path
      .split('/')
      .map { segment =>
        detectionChain.find { case (regex, _) =>
          regex.findPrefixOf(segment).isDefined
        }.map(_._2).getOrElse(segment)
      }
      .mkString("", "/", if (path.endsWith("/")) "/" else "")
      .taggedWith[PathTag]
}
