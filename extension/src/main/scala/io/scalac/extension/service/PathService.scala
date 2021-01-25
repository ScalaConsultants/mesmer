package io.scalac.extension.service
import io.scalac.extension.model.Path

trait PathService {

  def template(path: Path): Path
}
