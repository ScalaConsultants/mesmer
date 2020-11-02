package io.scalac.infrastructure

import slick.jdbc.JdbcProfile

trait Profile {
  type P <: JdbcProfile
  val profile: P
}
