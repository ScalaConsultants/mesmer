package io.scalac

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.newrelic.telemetry.opentelemetry.`export`.NewRelicExporters
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.scalac.api.AccountRoutes
import io.scalac.domain.AccountStateActor.Command
import io.scalac.domain.{ AccountStateActor, JsonCodecs }
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.StdIn
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

object Boot extends App with FailFastCirceSupport with JsonCodecs {

  val logger         = LoggerFactory.getLogger(Boot.getClass)
  val inMemoryConfig = ConfigFactory.parseString("""
                                                   |akka {
                                                   |  persistence {
                                                   |    journal {
                                                   |      plugin = "akka.persistence.journal.inmem"
                                                   |      auto-start-journals = ["akka.persistence.journal.inmem"]
                                                   |    }
                                                   |  }
                                                   |}
                                                   |""".stripMargin)

  val baseConfig = ConfigFactory
    .load()
    .withFallback(
      ConfigFactory
        .empty()
        .withValue(
          "app",
          ConfigValueFactory
            .fromMap(Map("host" -> "localhost", "port" -> 8080, "snapshot-every" -> 10, "keep-snapshots" -> 2).asJava)
        )
    )
    .resolve

  def startUp(inMemoryJournal: Boolean): Unit = {

    val config = if (inMemoryJournal) inMemoryConfig.withFallback(baseConfig) else baseConfig

    implicit val system: ActorSystem[Nothing]       = ActorSystem[Nothing](Behaviors.empty, "Accounts", config)
    implicit val executionContext: ExecutionContext = system.executionContext
    implicit val timeout: Timeout                   = 10 seconds

    val entity = EntityTypeKey[AccountStateActor.Command]("accounts")

    val createActorFromUUid: UUID => Behavior[Command] =
      AccountStateActor(_, config.getInt("app.snapshot-every"), config.getInt("app.keep-snapshots"))

    val accountsShards = ClusterSharding(system)
      .init(Entity(entity)(entityContext => createActorFromUUid(UUID.fromString(entityContext.entityId))))

    AkkaManagement(system)
      .start()
      .onComplete {
        case Success(value)     => logger.info(s"Started akka management on uri: ${value}")
        case Failure(exception) => logger.error("Coundn't start akka management", exception)
      }

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
        }
    }

    StdIn.readLine()
  }

  val inMemoryJournal = Try(baseConfig.getBoolean("app.in_memory_journal")).getOrElse(false)
  startUp(inMemoryJournal)

}
