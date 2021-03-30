package io.scalac.extension

import akka.cluster.sharding.typed.ShardingEnvelope
import io.scalac.core.model._
import io.scalac.extension.metric.ClusterMetricsMonitor.Labels
import io.scalac.extension.util.probe.BoundTestProbe._
import io.scalac.extension.util.{ SingleNodeClusterSpec, TestBehavior }
import org.scalatest.Inspectors
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.language.postfixOps

class ClusterSelfNodeEventsActorTest extends AsyncFlatSpec with SingleNodeClusterSpec with Matchers with Inspectors {

  import util.TestBehavior.Command._

  "ClusterSelfNodeEventsActor" should "show proper amount of entities per region" in setup(TestBehavior.apply) {
    case (system, member, ref, monitor, region) =>
      system.systemActorOf(ClusterRegionsMonitorActor(monitor), "sut")
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Create)
      val messages = monitor.entityPerRegionProbe.receiveMessages(2, 3 * pingOffset)
      messages should contain(MetricObserved(10, Labels(member.uniqueAddress.toNode, Some(region))))
  }

  it should "show a amount of shards per region" in setup(TestBehavior.apply) {
    case (system, member, ref, monitor, region) =>
      system.systemActorOf(ClusterRegionsMonitorActor(monitor), "sut")
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Create)
      val messages       = monitor.shardPerRegionsProbe.receiveMessages(2, 3 * pingOffset)
      val ExpectedLabels = Labels(member.uniqueAddress.toNode, Some(region))
      forAtLeast(1, messages)(
        _ should matchPattern {
          case MetricObserved(value, ExpectedLabels) if value > 0 =>
        }
      )
  }

  it should "show proper amount of entities on node" in setup(TestBehavior.apply) {
    case (system, member, ref, monitor, _) =>
      system.systemActorOf(ClusterRegionsMonitorActor(monitor), "sut")
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Create)
      val messages = monitor.entitiesOnNodeProbe.receiveMessages(2, 3 * pingOffset)
      messages should contain(MetricObserved(10, Labels(member.uniqueAddress.toNode)))
  }

  it should "show proper amount of entities on node with 2 regions" in setupN(TestBehavior.apply, n = 2) {
    case (system, member, refs, monitor, _) =>
      system.systemActorOf(ClusterRegionsMonitorActor(monitor), "sut")
      for (i <- 0 until 10) refs(i % refs.length) ! ShardingEnvelope(s"test_$i", Create)
      val messages = monitor.entitiesOnNodeProbe.receiveMessages(2, 3 * pingOffset)
      messages should contain(MetricObserved(10, Labels(member.uniqueAddress.toNode)))
  }

  it should "show proper amount of reachable nodes" in setup(TestBehavior.apply) { case (system, _, _, monitor, _) =>
    system.systemActorOf(ClusterSelfNodeEventsActor(monitor), "sut")
    monitor.reachableNodesProbe.within(5 seconds) {
      val probe = monitor.reachableNodesProbe
      probe.receiveMessage() shouldEqual (Inc(1L))
      probe.expectNoMessage(probe.remaining)
    }
    monitor.unreachableNodesProbe.expectNoMessage()
    succeed
  }

}
