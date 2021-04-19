package io.scalac.extension

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.Cluster
import akka.remote.testkit.MultiNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction
import io.scalac.core.util.ScalaTestMultiNodeSpec
import io.scalac.core.util.probe.BoundTestProbe.{Dec, Inc}
import io.scalac.core.util.probe.ClusterMetricsTestProbe
import io.scalac.core.util.probe.ObserverCollector.ScheduledCollectorImpl
import org.scalatest.Inspectors

import scala.concurrent.duration._
import scala.language.postfixOps

class ReachabilityTestMultiJvmNode1 extends ReachabilityTest
class ReachabilityTestMultiJvmNode2 extends ReachabilityTest
class ReachabilityTestMultiJvmNode3 extends ReachabilityTest

class ReachabilityTest extends MultiNodeSpec(ThreeNodesConfig) with ScalaTestMultiNodeSpec with Inspectors {
  def initialParticipants: Int = 3

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  val monitor = ClusterMetricsTestProbe(new ScheduledCollectorImpl(5.seconds))

  import ThreeNodesConfig._

  "Reachability test" should {
    "Wait for all nodes to join the cluster" in {
      Cluster(system) join node(node1).address
      enterBarrier("cluster initialized")
    }

    "start monitor" in {
      system.log.error("Address, {}", node(myself).address)
      typedSystem.systemActorOf(ClusterSelfNodeEventsActor.apply(monitor), "monitor-test-1")

      enterBarrier("monitor-up")
    }

    "show all nodes as reachable" in {

      monitor.reachableNodesProbe.within(5 seconds) {
        val probe = monitor.reachableNodesProbe
        forAll(probe.receiveMessages(roles.size))(_ shouldBe Inc(1L))
        probe.expectNoMessage(probe.remaining)
      }

      enterBarrier("all-reachable")
    }

    "show node2 as unreachable" in {

      runOn(node1) {
        testConductor.blackhole(node1, node2, Direction.Both)
        testConductor.blackhole(node3, node2, Direction.Both)
      }

      enterBarrier("network-partition")

      runOn(node1, node3) {
        monitor.reachableNodesProbe.within(10 seconds) {
          val reachableProbe   = monitor.reachableNodesProbe
          val unreachableProbe = monitor.unreachableNodesProbe
          reachableProbe.receiveMessage() shouldBe Dec(1L)
          unreachableProbe.receiveMessage() shouldBe Inc(1L)
        }

        monitor.unreachableNodesProbe.expectNoMessage()
        monitor.reachableNodesProbe.expectNoMessage()
      }
      enterBarrier("after-network-partition-check")
    }

    "Show node2 as reachable again" in {

      runOn(node1) {
        testConductor.passThrough(node1, node2, Direction.Both)
        testConductor.passThrough(node3, node2, Direction.Both)
      }

      enterBarrier("remove-network-partition")

      runOn(node1, node3) {

        monitor.reachableNodesProbe.within(10 seconds) {
          val reachableProbe   = monitor.reachableNodesProbe
          val unreachableProbe = monitor.unreachableNodesProbe
          reachableProbe.receiveMessage() shouldBe Inc(1L)
          unreachableProbe.receiveMessage() shouldBe Dec(1L)
        }

        monitor.unreachableNodesProbe.expectNoMessage()
        monitor.reachableNodesProbe.expectNoMessage()
      }

      enterBarrier("node2-recovered")
    }

    "Show only 2 reachable nodes after node2 leave cluster" in {
      runOn(node2) {
        Cluster(system).leave(node(myself).address)
      }

      enterBarrier("node2-leave")

      runOn(node1, node3) {
        monitor.reachableNodesProbe.within(5 seconds) {
          val reachableProbe = monitor.reachableNodesProbe
          reachableProbe.receiveMessage() shouldBe Dec(1L)
        }
        monitor.unreachableNodesProbe.expectNoMessage()
        monitor.reachableNodesProbe.expectNoMessage()
      }

      enterBarrier("test-end")
    }
  }
}
