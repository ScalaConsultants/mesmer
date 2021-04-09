package io.scalac.extension.service
import io.scalac.core.model.Path

trait PathService {

  def template(path: Path): Path
}

object PathService {
  val numberTemplate = "{num}"
  val uuidTemplate   = "{uuid}"
}
