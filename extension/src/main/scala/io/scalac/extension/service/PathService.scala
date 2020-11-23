package io.scalac.extension.service
import io.scalac.`extension`.model.Path

import scala.util.matching.Regex

trait PathService {

  def template(path: Path): Path
}

class CommonRegexPathService extends PathService {
  private val uuid: Regex = """[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r
  private val number: Regex = """[+-]?\d*\.?\d*""".r

  private val detectionChain = List((number, "<num>"), (uuid, "<uuid>"))

  override def template(path: Path): Path = path
    .split('/')
    .map {
      case segment if segment.nonEmpty => detectionChain.flatMap {
        case (regex, template) => regex.unapplySeq(segment).map(_ => template)
      }.headOption.getOrElse(segment)
      case segment => segment
    }.mkString("/")
}
