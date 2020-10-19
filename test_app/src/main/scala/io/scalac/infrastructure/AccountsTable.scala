package io.scalac.infrastructure

import io.scalac.domain.Account
import io.scalac.domain.AccountId
import java.{ util => ju }

trait AccountsTable {
  self: Profile =>
  import profile.api._

  class Accounts(tag: Tag) extends Table[Account](tag, "accounts") {
    def id      = column[String]("id", O.PrimaryKey)
    def balance = column[Double]("balance")

    def * =
      (id, balance).<>({
        case (id, balance) => Account(ju.UUID.fromString(id), balance)
      }, (account: Account) => Some(account.id.toString, account.balance))
  }

}
