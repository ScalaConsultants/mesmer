package io.scalac.extension.util

import java.util.UUID

trait TestOps {

  def createUniqueId: String = UUID.randomUUID().toString

}
