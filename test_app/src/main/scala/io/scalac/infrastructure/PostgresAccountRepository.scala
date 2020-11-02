package io.scalac.infrastructure

import io.scalac.domain.{Account, AccountId, AccountRepository}
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class PostgresAccountRepository(db: Database)
    extends AccountsTable
    with Profile
    with AccountRepository {

  type P = PostgresProfile
  override lazy val profile: PostgresProfile = PostgresProfile

  val accountTable = TableQuery[Accounts]

  override def getAccount(id: AccountId): Future[Option[Account]] = {
    val query = accountTable
      .filter(_.id === id.toString.toLowerCase())
      .result
      .headOption
    db.run(query)
  }

  override def getOrCreateAccount(id: AccountId, defaultBalance: Double)(
    implicit ec: ExecutionContext
  ): Future[Account] = {
    val query = accountTable
      .filter(_.id === id.toString().toLowerCase)
      .result
      .headOption
      .flatMap {
        case Some(value) => DBIO.successful(value)
        case None =>
          (accountTable returning accountTable) += Account(id, defaultBalance)
      }
    db.run(query)
  }

  override def update(
    account: Account
  )(implicit ec: ExecutionContext): Future[Double] = {
    val query = accountTable
      .filter(_.id === account.id.toString().toLowerCase)
      .map(_.balance)
      .update(account.balance)
      .map(_ => account.balance)

    db.run(query)
  }

  def createTableIfNotExists: Future[Unit] =
    db.run(accountTable.schema.createIfNotExists)

  override def insert(
    account: Account
  )(implicit ec: ExecutionContext): Future[Double] = {
    val query = (accountTable returning accountTable.map(_.balance)) += account
    db.run(query)
  }
}
