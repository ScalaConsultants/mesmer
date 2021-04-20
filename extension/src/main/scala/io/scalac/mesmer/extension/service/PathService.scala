package io.scalac.mesmer.extension.service
import scala.util.matching.Regex

import io.scalac.mesmer.core.model.Path

trait PathService {

  def template(path: Path): Path
}

object PathService {
  final val numberTemplate = "{num}"
  final val uuidTemplate   = "{uuid}"

  val numberRegex: Regex = """^[+-]?\d+\.?\d*$""".r
  val uuidRegex: Regex   = """^[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}$""".r
}
