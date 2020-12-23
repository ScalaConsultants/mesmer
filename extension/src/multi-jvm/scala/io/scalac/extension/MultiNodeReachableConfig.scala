package io.scalac.extension

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.Cluster
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import akka.remote.transport.ThrottlerTransportAdapter.Direction
import com.typesafe.config.ConfigFactory
import io.scalac.extension.util.BoundTestProbe.{ Dec, Inc }
import io.scalac.extension.util.{ ClusterMetricsTestProbe, ScalaTestMultiNodeSpec }
import org.scalatest.Inspectors

import scala.concurrent.duration._
import scala.language.postfixOps

class ReachabilityTestMultiJvmNode1 extends ReachabilityTest
class ReachabilityTestMultiJvmNode2 extends ReachabilityTest

object MultiNodeReachableConfig extends MultiNodeConfig {

  val node1 = role("node1")
  val node2 = role("node2")

  testTransport(true)
  commonConfig(ConfigFactory.parseString("""
                                           |akka.loglevel=DEBUG
                                           |akka.actor.provider = cluster
    """.stripMargin))
//  commonConfig(ConfigFactory.parseString("""
//                                           |akka.loglevel=DEBUG
//                                           |akka.actor.provider = cluster
//                                           |akka.remote.artery.enabled = on
//                                           |akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
//                                           |akka.coordinated-shutdown.terminate-actor-system = off
//                                           |akka.cluster.run-coordinated-shutdown-when-down = off
//    """.stripMargin))
}

class ReachabilityTest extends MultiNodeSpec(MultiNodeReachableConfig) with ScalaTestMultiNodeSpec with Inspectors {
  override def initialParticipants: Int = roles.size

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped
  import MultiNodeReachableConfig._
  "Reachability test" should {
    "wait for all nodes to start" in {
      enterBarrier("init")
    }

    "wait for all nodes to join the clucster" in {
      Cluster(system) join node(node1).address
      enterBarrier("cluster initialized")
    }

    "show valid amount of reachable nodes" in {

      val monitor = ClusterMetricsTestProbe()

      val monitoring = typedSystem.systemActorOf(ClusterSelfNodeEventsActor.apply(monitor), "monitor-test-1")

      enterBarrier("monitor-up")

      monitor.reachableNodesProbe.within(5 seconds) {
        val probe = monitor.reachableNodesProbe
        forAll(probe.receiveMessages(2))(_ shouldBe Inc(1L))
        probe.expectNoMessage(probe.remaining)
      }

      enterBarrier("all-reachable")



      runOn(node1) {
        testConductor.blackhole(node1, node2, Direction.Both)

        monitor.reachableNodesProbe.within(10 seconds) {
          val reachableProbe   = monitor.reachableNodesProbe
          val unreachableProbe = monitor.unreachableNodesProbe
          reachableProbe.receiveMessage() shouldBe Dec(1L)
          unreachableProbe.receiveMessage() shouldBe Inc(1L)
        }

        monitor.unreachableNodesProbe.expectNoMessage()
        monitor.reachableNodesProbe.expectNoMessage()
      }
    }
  }
}
