package io.scalac.extension

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorSystem, SupervisorStrategy }
import akka.cluster.Cluster
import akka.cluster.typed.{ ClusterSingleton, SingletonActor }
import akka.remote.testkit.MultiNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction
import io.scalac.extension.ThreeNodesConfig._
import io.scalac.extension.util.probe.BoundTestProbe.{ Dec, Inc }
import io.scalac.extension.util.ScalaTestMultiNodeSpec
import org.scalatest.Inspectors

import scala.concurrent.duration._
import scala.language.postfixOps

class DownTestMultiJvmNode1 extends DownTest
class DownTestMultiJvmNode2 extends DownTest
class DownTestMultiJvmNode3 extends DownTest

class DownTest extends MultiNodeSpec(ThreeNodesConfig) with ScalaTestMultiNodeSpec with Inspectors {
  override def initialParticipants: Int = 3

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  val monitor = ClusterMetricsTestProbe()

  "Node down" should {
    "Wait for all nodes to join the cluster" in {
      Cluster(system) join node(node1).address
      enterBarrier("cluster initialized")
    }

    "start monitor" in {
      system.log.error(s"Address, ${node(myself).address}")
      typedSystem.systemActorOf(ClusterSelfNodeEventsActor.apply(monitor), "monitor-test-1")
      ClusterSingleton(typedSystem)
        .init(
          SingletonActor(
            Behaviors
              .supervise(OnClusterStartUp(_ => ClusterEventsMonitor(monitor), None))
              .onFailure[Exception](SupervisorStrategy.restart),
            "MemberMonitoringActor"
          )
        )

      runOn(node1) {
        monitor.nodeDownProbe.receiveMessage(10 seconds) shouldBe Inc(0L) // send to start exporting metrics to backend
      }

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

    "show node down and substract unreachable nodes" in {
      runOn(node1) {
        Cluster(system).down(node(node2).address)
      }

      enterBarrier("node2 downed")

      runOn(node1, node3) {
        monitor.unreachableNodesProbe.receiveMessage(5 seconds) shouldBe Dec(1L)
        monitor.unreachableNodesProbe.expectNoMessage()
        monitor.reachableNodesProbe.expectNoMessage()
      }

      runOn(node1) {
        monitor.nodeDownProbe.receiveMessage() shouldBe Inc(1L)
        monitor.nodeDownProbe.expectNoMessage()
      }

      enterBarrier("test-finished")
    }
  }

}
