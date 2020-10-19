package io.scalac.domain
import zio._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait AccountRepository {
    def getAccount(id: AccountId): Future[Option[Account]]
    def getOrCreateAccount(id: AccountId, defaultBalance: Double = 0.0)(implicit ec: ExecutionContext): Future[Account]
    def update(account: Account)(implicit ec: ExecutionContext): Future[Double]
    def insert(account: Account)(implicit ec: ExecutionContext): Future[Double]
}
