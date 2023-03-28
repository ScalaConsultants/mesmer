package io.scalac.mesmer.instrumentation.akka.cluster

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.{ typed, ActorSystem }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory
import io.scalac.mesmer.agent.utils.OtelAgentTest
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterRegionsMonitorActor.Command
import io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterSelfNodeEventsActor
import io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterSelfNodeEventsActor.Command.NodeUnreachable
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Seconds, Span }

final class AkkaClusterTest extends AnyFlatSpec with OtelAgentTest with TestOps with Matchers with BeforeAndAfterEach {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(1, Seconds)))

  implicit val system: typed.ActorSystem[Nothing] =
    ActorSystem("test-system", ConfigFactory.load("cluster-application-test")).toTyped

  override protected def beforeAll(): Unit = {
    Cluster(system)
    super.beforeAll()
  }

  it should "record nodes down metric" in {
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_cluster_node_down")
  }

  it should "record reachable nodes metric" in {
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_cluster_reachable_nodes")
  }

  it should "record unreachable nodes metric" in {
    val message = NodeUnreachable(Cluster(system).selfMember.uniqueAddress)
    val actor   = system.systemActorOf(ClusterSelfNodeEventsActor(), "sut")
    actor.tell(message)

    assertMetricSumGreaterOrEqualTo0("mesmer_akka_cluster_unreachable_nodes")
  }

  it should "record entities per region metric" in {
    useSharding()
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_cluster_entities_per_region")
  }

  it should "record cluster shards per region metric" in {
    useSharding()
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_cluster_shards_per_region")
  }

  it should "record cluster entities on node metric" in {
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_cluster_entities_on_node")
  }

  it should "record cluster shard regions on node metric" in {
    assertMetricSumGreaterOrEqualTo0("mesmer_akka_cluster_shard_regions_on_node")
  }

  private def useSharding(): ActorRef[ShardingEnvelope[Command]] = {
    val entity = EntityTypeKey[Command]("foo")
    ClusterSharding(system)
      .init(Entity(entity) { _ =>
        Behaviors.empty
      })
  }
}
