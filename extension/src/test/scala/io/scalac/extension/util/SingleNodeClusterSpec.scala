package io.scalac.extension.util

import java.util.UUID

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.Member
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.cluster.typed.{Cluster, SelfUp, Subscribe}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.scalac.extension.util.probe.ClusterMetricsTestProbe
import org.scalatest.{Assertion, AsyncTestSuite}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.reflect.ClassTag

trait SingleNodeClusterSpec extends AsyncTestSuite {

  protected val portGenerator: PortGenerator = PortGeneratorImpl
  implicit val timeout: Timeout              = 30 seconds

  protected def createConfig(port: Int, systemName: String): Config = {
    val hostname = "127.0.0.1"
    val seedNode = s"akka://${systemName}@${hostname}:${port}"
    ConfigFactory.empty
      .withValue("akka.actor.provider", ConfigValueFactory.fromAnyRef("cluster"))
      .withValue(
        "akka.remote.artery.canonical",
        ConfigValueFactory.fromMap(Map("hostname" -> "127.0.0.1", "port" -> port).asJava)
      )
      .withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(List(seedNode).asJava))
  }

  type Fixture[T] = (ActorSystem[Nothing], Member, ActorRef[ShardingEnvelope[T]], ClusterMetricsTestProbe, String)

  def setup[T: ClassTag](behavior: String => Behavior[T])(test: Fixture[T] => Assertion): Future[Assertion] = {
    val port = portGenerator.generatePort()

    def initAkka(): Future[(String, ActorSystem[Nothing], Cluster, ClusterSharding)] = Future {
      val systemId: String   = UUID.randomUUID().toString
      val entityName: String = UUID.randomUUID().toString
      val systemConfig       = createConfig(port.port, systemId)

      implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, systemId, systemConfig)
      // consider using blocking dispatcher
      val cluster = Cluster(system)

      val sharding = ClusterSharding(system)
      (entityName, system, cluster, sharding)
    }

    def runTest(
      system: ActorSystem[Nothing],
      entityName: String,
      cluster: Cluster,
      sharding: ClusterSharding
    ): Future[Assertion] =
      Future {
        val entityKey = EntityTypeKey[T](entityName)
        val entity    = Entity(entityKey)(context => behavior(context.entityId))

        // sharding boots-up synchronously
        val ref          = sharding.init(entity)
        val clusterProbe = ClusterMetricsTestProbe()(system)

        Function.untupled(test)(system, cluster.selfMember, ref, clusterProbe, entityName)
      }

    def onClusterStart(cluster: Cluster)(implicit system: ActorSystem[_]) =
      cluster.subscriptions.ask[SelfUp](reply => Subscribe(reply, classOf[SelfUp]))

    for {
      (region, _system, cluster, sharding) <- initAkka()
      system                               = _system
      _                                    <- onClusterStart(cluster)(system)
      assertion                            <- runTest(system, region, cluster, sharding)
      _                                    = portGenerator.releasePort(port)
      _                                    = system.terminate()
      _                                    <- system.whenTerminated
    } yield assertion
  }

}
