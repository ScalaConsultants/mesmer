package io.scalac.extension.util

import io.scalac.extension.model.Path
import io.scalac.extension.service.PathService

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

object IdentityPathService extends PathService {
  override def template(path: Path): Path = path
}


