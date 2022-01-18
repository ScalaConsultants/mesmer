package io.scalac.mesmer.extension

import akka.cluster.sharding.typed.ShardingEnvelope
import org.scalatest.Inspectors
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.TestBehaviors
import io.scalac.mesmer.core.util.TestBehaviors.SameStop.Command.Same
import io.scalac.mesmer.extension.metric.ClusterMetricsMonitor.Attributes
import io.scalac.mesmer.extension.util.SingleNodeClusterSpec
import io.scalac.mesmer.extension.util.probe.BoundTestProbe._

class ClusterSelfNodeEventsActorTest extends AsyncFlatSpec with SingleNodeClusterSpec with Matchers with Inspectors {

  "ClusterSelfNodeEventsActor" should "show proper amount of entities per region" in setup(
    TestBehaviors.SameStop.apply
  ) { case (system, member, ref, monitor, region) =>
    system.systemActorOf(ClusterRegionsMonitorActor(monitor), "sut")
    for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Same)
    val messages = monitor.entityPerRegionProbe.receiveMessages(2, 3 * pingOffset)
    messages should contain(MetricObserved(10, Attributes(member.uniqueAddress.toNode, Some(region))))
  }

  it should "show a amount of shards per region" in setup(TestBehaviors.SameStop.apply) {
    case (system, member, ref, monitor, region) =>
      system.systemActorOf(ClusterRegionsMonitorActor(monitor), "sut")
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Same)
      val messages           = monitor.shardPerRegionsProbe.receiveMessages(2, 3 * pingOffset)
      val ExpectedAttributes = Attributes(member.uniqueAddress.toNode, Some(region))
      forAtLeast(1, messages)(
        _ should matchPattern {
          case MetricObserved(value, ExpectedAttributes) if value > 0 =>
        }
      )
  }

  it should "show proper amount of entities on node" in setup(TestBehaviors.SameStop.apply) {
    case (system, member, ref, monitor, _) =>
      system.systemActorOf(ClusterRegionsMonitorActor(monitor), "sut")
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Same)
      val messages = monitor.entitiesOnNodeProbe.receiveMessages(2, 3 * pingOffset)
      messages should contain(MetricObserved(10, Attributes(member.uniqueAddress.toNode)))
  }

  it should "show proper amount of entities on node with 2 regions" in setupN(TestBehaviors.SameStop.apply, n = 2) {
    case (system, member, refs, monitor, _) =>
      system.systemActorOf(ClusterRegionsMonitorActor(monitor), "sut")
      for (i <- 0 until 10) refs(i % refs.length) ! ShardingEnvelope(s"test_$i", Same)
      val messages = monitor.entitiesOnNodeProbe.receiveMessages(2, 3 * pingOffset)
      messages should contain(MetricObserved(10, Attributes(member.uniqueAddress.toNode)))
  }

  it should "show proper amount of reachable nodes" in setup(TestBehaviors.SameStop.apply) {
    case (system, _, _, monitor, _) =>
      system.systemActorOf(ClusterSelfNodeEventsActor(monitor), "sut")
      monitor.reachableNodesProbe.within(5.seconds) {
        val probe = monitor.reachableNodesProbe
        probe.receiveMessage() shouldEqual (Inc(1L))
        probe.expectNoMessage(probe.remaining)
      }
      monitor.unreachableNodesProbe.expectNoMessage()
      succeed
  }

}
