package io.scalac.mesmer.extension.util

import io.scalac.mesmer.core.model.Path
import io.scalac.mesmer.extension.service.PathService

object IdentityPathService extends PathService {
  def template(path: Path): Path = path
}
