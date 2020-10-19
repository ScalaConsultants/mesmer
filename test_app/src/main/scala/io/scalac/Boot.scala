package io.scalac

import akka.actor.typed.ActorSystem

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http
import scala.io.StdIn
import io.scalac.domain._
import java.{ util => ju }
import io.scalac.domain.JsonCodecs
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import akka.actor.typed.ActorRef
import io.scalac.domain.AccountManagerActor
import slick.jdbc.PostgresProfile.api.Database
import com.typesafe.config.ConfigFactory
import io.scalac.infrastructure.PostgresAccountRepository
import akka.actor.typed.delivery.ConsumerController.Command
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }
import io.scalac.domain.AccountActor
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.actor.Status
import io.scalac.api.AccountRoutes
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http
import scala.io.StdIn
import io.scalac.domain._
import java.{ util => ju }
import io.scalac.domain.JsonCodecs
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import akka.actor.typed.ActorRef
import io.scalac.domain.AccountManagerActor
import slick.jdbc.PostgresProfile.api.Database
import com.typesafe.config.ConfigFactory
import io.scalac.infrastructure.PostgresAccountRepository
import akka.actor.typed.delivery.ConsumerController.Command
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }
import io.scalac.domain.AccountActor
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.actor.Status
import com.typesafe.config.ConfigValueFactory
import scala.jdk.CollectionConverters._
import io.scalac.extension.ListeningActor
import akka.cluster.typed.ClusterSingleton
import akka.cluster.typed.SingletonActor
import akka.actor.typed.SupervisorStrategy

object Boot extends App with FailFastCirceSupport with JsonCodecs {

  val config = ConfigFactory
    .load()
    .withFallback(
      ConfigFactory
        .empty()
        .withValue("app", ConfigValueFactory.fromMap(Map("host" -> "localhost", "port" -> 8080).asJava))
    )
  val accountRepository = new PostgresAccountRepository(Database.forConfig("db", config))

  implicit val system           = ActorSystem[Nothing](Behaviors.empty, "Accounts", config)
  implicit val executionContext = system.executionContext
  implicit val timeout: Timeout = 2 seconds

  val entity = EntityTypeKey[AccountActor.Command]("accounts")
  // val monitoringSingleton = ClusterSingleton(system).init(
  //   SingletonActor(
  //     Behaviors.supervise(ListeningActor()).onFailure[Exception](SupervisorStrategy.restart),
  //     "MemberMonitoringActor"
  //   )
  // )

  val accountsShards = ClusterSharding(system)
    .init(Entity(entity) { entityContext =>
      AccountActor(accountRepository, ju.UUID.fromString(entityContext.entityId))
    })

  val accountRoutes = new AccountRoutes(accountsShards)

  val binding =
    accountRepository.createTableIfNotExists.flatMap(_ =>
      Http().newServerAt(config.getString("app.host"), config.getInt("app.port")).bind(accountRoutes.routes)
    )

  StdIn.readLine()

  binding
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
