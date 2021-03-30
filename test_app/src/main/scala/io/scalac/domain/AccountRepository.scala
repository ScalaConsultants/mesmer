package io.scalac.domain
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait AccountRepository {
  def getAccount(id: AccountId): Future[Option[Account]]
  def getOrCreateAccount(id: AccountId, defaultBalance: Double = 0.0)(implicit
    ec: ExecutionContext
  ): Future[Account]
  def update(account: Account)(implicit ec: ExecutionContext): Future[Double]
  def insert(account: Account)(implicit ec: ExecutionContext): Future[Double]
}
