package example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import example.api.AccountRoutes
import example.domain.AccountStateActor
import example.domain.JsonCodecs
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.StdIn
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

object Boot extends App with FailFastCirceSupport with JsonCodecs {
  val logger: Logger = LoggerFactory.getLogger(Boot.getClass)

  private val fallbackConfig = ConfigFactory
    .empty()
    .withValue(
      "app",
      ConfigValueFactory
        .fromMap(Map("host" -> "localhost", "port" -> 8080, "systemName" -> "Accounts").asJava)
    )
    .resolve

  def startUp(): Unit = {
    logger.info("Starting application with static seed nodes")
    val baseConfig = ConfigFactory.load()

    val config =
      baseConfig
        .withFallback(fallbackConfig)
        .resolve

    val systemName = config.getString("app.systemName")
    val host       = config.getString("app.host")
    val port       = config.getInt("app.port")

    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, systemName, config)

    implicit val executionContext: ExecutionContext = system.executionContext
    implicit val timeout: Timeout                   = 10 seconds

    AkkaManagement(system).start().onComplete {
      case Success(value)     => logger.info("Started Akka Management on uri: {}", value)
      case Failure(exception) => logger.error("Couldn't start Akka Management", exception)
    }

    val entity = EntityTypeKey[AccountStateActor.Command]("accounts")

    val accountsShards = ClusterSharding(system)
      .init(Entity(entity) { entityContext =>
        AccountStateActor(
          java.util.UUID.fromString(entityContext.entityId)
        )
      })

    val accountRoutes = new AccountRoutes(accountsShards)

    logger.info(s"Starting http server at $host:$port")
    val binding = Http()
      .newServerAt(host, port)
      .bind(accountRoutes.routes)

    StdIn.readLine()

    binding
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }

  startUp()
}
