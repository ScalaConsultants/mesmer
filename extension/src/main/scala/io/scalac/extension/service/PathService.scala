package io.scalac.extension.service
import io.scalac.`extension`.model.Path

trait PathService {

  def template(path: Path): Path
}

class CommonRegexPathService extends PathService {
  private val uuid   = """^[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}$""".r
  private val number = """^[+-]?\d+\.?\d*$""".r

  private val detectionChain = List((number, "<num>"), (uuid, "<uuid>"))

  override def template(path: Path): Path =
    path
      .split('/')
      .map { segment =>
        detectionChain.find {
          case (regex, _) => regex.findPrefixOf(segment).isDefined
        }.map(_._2).getOrElse(segment)
      }
      .mkString("/")
}
