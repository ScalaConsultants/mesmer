package io.scalac.mesmer.instrumentation.akka.cluster

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory
import io.scalac.mesmer.agent.utils.OtelAgentTest
import io.scalac.mesmer.core.util.TestOps
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Seconds, Span }

import scala.jdk.CollectionConverters._

final class AkkaClusterTest extends AnyFlatSpec with OtelAgentTest with TestOps with Matchers with BeforeAndAfterEach {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(1, Seconds)))

  override protected def beforeEach(): Unit = super.beforeEach()

  it should "record nodes down metric" in {
    val system = ActorSystem("test-system", ConfigFactory.load("cluster-application-test")).toTyped
    Cluster(system)

    assertMetric("mesmer_akka_cluster_node_down_total") { data =>
      val totalCount = data.getLongSumData.getPoints.asScala.map(_.getValue).sum
      totalCount should be >= 0L
    }
  }
}
