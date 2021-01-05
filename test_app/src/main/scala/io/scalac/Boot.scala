package io.scalac

import java.{ util => ju }

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.newrelic.telemetry.opentelemetry.`export`.NewRelicExporters
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.scalac.api.AccountRoutes
import io.scalac.domain.{ AccountStateActor, JsonCodecs }
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

object Boot extends App with FailFastCirceSupport with JsonCodecs {
  val logger = LoggerFactory.getLogger(Boot.getClass)

  private val fallbackConfig = ConfigFactory
    .empty()
    .withValue(
      "app",
      ConfigValueFactory
        .fromMap(Map("host" -> "localhost", "port" -> 8080, "systemName" -> "Accounts").asJava)
    )

  def startUp(local: Boolean): Unit = {
    val baseConfig =
      if (local) ConfigFactory.load("local/application")
      else ConfigFactory.load()

    println(baseConfig.getString("akka.actor.provider"))
    println(baseConfig.getString("newrelic.account_id"))
    println(baseConfig.getStringList("akka.cluster.seed-nodes"))

    val config =
      baseConfig
        .withFallback(fallbackConfig)
        .resolve

    implicit val system =
      ActorSystem[Nothing](Behaviors.empty, config.getString("app.systemName"), config)

    implicit val executionContext = system.executionContext
    implicit val timeout: Timeout = 10 seconds

    if (!local) {
      ClusterBootstrap(system).start()
    }

    AkkaManagement(system).start()

    val entity = EntityTypeKey[AccountStateActor.Command]("accounts")

    val accountsShards = ClusterSharding(system)
      .init(Entity(entity) { entityContext =>
        AccountStateActor(
          ju.UUID.fromString(entityContext.entityId)
        )
      })

    val accountRoutes = new AccountRoutes(accountsShards)

    val host = config.getString("app.host")

    val port = config.getInt("app.port")
    logger.info(s"Starting http server at $host:$port")

    val binding = Http()
      .newServerAt(host, port)
      .bind(accountRoutes.routes)

    sys.addShutdownHook {
      binding
        .flatMap(_.unbind())
        .onComplete { _ =>
          system.terminate()
          NewRelicExporters.shutdown()
        }
    }
  }

  val local = sys.props.get("env").exists(_.toLowerCase() == "local")
  if (local) {
    logger.info("Starting application with static seed nodes")
  } else {
    logger.info("Staring application with ClusterBootstrap")
  }
  startUp(local)
}
