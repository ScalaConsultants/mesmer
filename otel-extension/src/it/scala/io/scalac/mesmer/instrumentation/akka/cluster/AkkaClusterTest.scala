package io.scalac.mesmer.instrumentation.akka.cluster

import akka.actor.{ typed, ActorSystem }
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory
import io.scalac.mesmer.agent.utils.OtelAgentTest
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterSelfNodeEventsActor
import io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterSelfNodeEventsActor.Command.NodeUnreachable
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Seconds, Span }

import scala.jdk.CollectionConverters._

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
    assertMetric("mesmer_akka_cluster_node_down_total") { data =>
      val totalCount = data.getLongSumData.getPoints.asScala.map(_.getValue).sum
      totalCount should be >= 0L
    }
  }

  it should "record reachable nodes metric" in {
    assertMetric("mesmer_akka_cluster_reachable_nodes") { data =>
      val totalCount = data.getLongSumData.getPoints.asScala.map(_.getValue).sum
      totalCount should be >= 0L
    }
  }

  it should "record unreachable nodes metric" in {
    val message = NodeUnreachable(Cluster(system).selfMember.uniqueAddress)
    val actor   = system.systemActorOf(ClusterSelfNodeEventsActor(), "sut")
    actor.tell(message)

    assertMetric("mesmer_akka_cluster_unreachable_nodes") { data =>
      val totalCount = data.getLongSumData.getPoints.asScala.map(_.getValue).sum
      totalCount should be >= 0L
    }
  }
}
