package io.scalac

import java.{util => ju}

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.http.scaladsl.Http
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.scalac.api.AccountRoutes
import io.scalac.domain.{AccountActor, JsonCodecs}
import io.scalac.infrastructure.PostgresAccountRepository
import slick.jdbc.PostgresProfile.api.Database

import scala.concurrent.duration._
import scala.io.StdIn
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

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
