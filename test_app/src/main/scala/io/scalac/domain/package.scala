package io.scalac

import java.{util => ju}

package object domain {
  type AccountId = ju.UUID

  object  AccountId {
    def apply(): AccountId = ju.UUID.randomUUID()
  }

  final case class Account(id: AccountId, balance: Double)

  final case class ApplicationError(message: String)
}
