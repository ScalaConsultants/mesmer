package io.scalac.extension

import java.util.UUID

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, DispatcherSelector }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.cluster.typed.{ Cluster, SelfUp, Subscribe }
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import io.scalac.extension.util.BoundTestProbe._
import io.scalac.extension.util.ClusterMetricsTestProbe
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.Random

class ClusterSelfNodeMetricGathererTest extends AsyncFlatSpec with Matchers {

  type Region     = String
  type Fixture[T] = (ActorSystem[Nothing], ActorRef[ShardingEnvelope[T]], ClusterMetricsTestProbe, Region)

  object TestBehavior {

    sealed trait Command

    object Command {

      case object Create extends Command
      case object Stop   extends Command
    }
    import Command._
    def apply(id: String): Behavior[Command] = Behaviors.receiveMessage {
      case Create => Behaviors.same
      case Stop   => Behaviors.stopped
    }
  }
  import TestBehavior.Command._

  def createConfig(port: Int, systemName: String): Config = {
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

  def setup[T: ClassTag](behavior: String => Behavior[T])(test: Fixture[T] => Assertion): Future[Assertion] = {

    val randomRemotingPort = Random.nextInt(20000) + 2000
    val systemId: String   = UUID.randomUUID().toString
    val entityName: String = UUID.randomUUID().toString
    val systemConfig       = createConfig(randomRemotingPort, systemId)

    implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, systemId, systemConfig)
    // consider using blocking dispatcher
    implicit val dispatcher: ExecutionContextExecutor = system.dispatchers.lookup(DispatcherSelector.default())
    implicit val bootTimeout: Timeout                 = 30 seconds

    val cluster = Cluster(system)

    val sharding = ClusterSharding(system)

    def runTest(): Future[Assertion] = Future {
      val entityKey = EntityTypeKey[T](entityName)
      val entity    = Entity(entityKey)(context => behavior(context.entityId))

      // sharding boots-up synchronously
      val ref = sharding
        .init(entity)

      val clusterProbe = ClusterMetricsTestProbe()
      Function.untupled(test)(system, ref, clusterProbe, entityName)
    }

    for {
      _         <- cluster.subscriptions.ask[SelfUp](reply => Subscribe(reply, classOf[SelfUp]))
      assertion <- runTest()
      _         = system.terminate()
      _         <- system.whenTerminated
    } yield assertion
  }

  "Monitoring" should "show proper amout of shards" in setup(TestBehavior.apply) {
    case (system, ref, monitor, region) =>
      val selfMonitor = system.systemActorOf(ClusterSelfNodeMetricGatherer.apply(monitor, List(region)), "sut")

      for {
        index <- 0 until 10
      } yield ref ! ShardingEnvelope(s"test_${index}", Create)

      val messages = monitor.entityPerRegionProbe.receiveMessages(2, 15 seconds)

      messages should contain(MetricRecorded(10))
  }

  it should "show proper amount of reachable nodes" in setup(TestBehavior.apply) {
    case (system, ref, monitor, region) =>
      val selfMonitor = system.systemActorOf(ClusterSelfNodeMetricGatherer.apply(monitor, List(region)), "sut")

      monitor.reachableNodesProbe.within(5 seconds) {
        import monitor.reachableNodesProbe._
        receiveMessage() shouldEqual (Inc(1L))
        expectNoMessage(remaining)
      }
      monitor.unreachableNodesProbe.expectNoMessage()
      succeed
  }
}
