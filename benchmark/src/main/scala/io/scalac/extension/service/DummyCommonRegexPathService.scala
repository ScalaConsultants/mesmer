package io.scalac.extension.service

import io.scalac.extension.model.Path

object DummyCommonRegexPathService extends PathService {

  override def template(path: Path): Path = path match {
    case get if get.contains("balance")           => "/api/v1/account/{uuid}/balance"
    case withdraw if withdraw.contains("balance") => "/api/v1/account/{uuid}/withdraw/{num}"
    case deposit if deposit.contains("balance")   => "/api/v1/account/{uuid}/deposit/{num}"
    case _                                        => "other"
  }
}
