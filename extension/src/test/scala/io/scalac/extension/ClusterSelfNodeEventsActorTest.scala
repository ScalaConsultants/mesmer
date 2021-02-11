package io.scalac.extension

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.cluster.sharding.typed.ShardingEnvelope

import org.scalatest.Inspectors
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.extension.event.EventBus
import io.scalac.extension.util.probe.BoundTestProbe._
import io.scalac.extension.util.{ ActorFailing, SingleNodeClusterSpec, TestBehavior }

class ClusterSelfNodeEventsActorTest
    extends AsyncFlatSpec
    with SingleNodeClusterSpec
    with Matchers
    with Inspectors
    with ActorFailing {

  import util.TestBehavior.Command._

  "ClusterSelfNodeEventsActor" should "show proper amount of entities per region" in setup(TestBehavior.apply) {
    case (system, _, ref, monitor, _) =>
      system.systemActorOf(ClusterRegionsMonitorActor.apply(monitor), "sut")
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Create)
      val messages = monitor.entityPerRegionProbe.receiveMessages(2, 15 seconds)
      messages should contain(MetricObserved(10))
  }

  it should "show a amount of shards per region" in setup(TestBehavior.apply) {
    case (system, _, ref, monitor, _) =>
      system.systemActorOf(ClusterRegionsMonitorActor.apply(monitor), "sut")
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Create)
      val messages = monitor.shardPerRegionsProbe.receiveMessages(2, 15 seconds)
      forAtLeast(1, messages)(
        _ should matchPattern {
          case MetricObserved(value) if value > 0 =>
        }
      )
  }

  it should "show proper amount of entities on node" in setup(TestBehavior.apply) {
    case (system, _, ref, monitor, _) =>
      system.systemActorOf(ClusterRegionsMonitorActor.apply(monitor), "sut")
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Create)
      val messages = monitor.entitiesOnNodeProbe.receiveMessages(2, 15 seconds)
      messages should contain(MetricObserved(10))
  }

  it should "show proper amount of entities on node with 2 regions" in setupN(TestBehavior.apply, n = 2) {
    case (system, _, refs, monitor, _) =>
      system.systemActorOf(ClusterRegionsMonitorActor.apply(monitor), "sut")
      val eventBus = EventBus(system)
      for (i <- 0 until 10) refs(i % refs.length) ! ShardingEnvelope(s"test_$i", Create)
      val messages = monitor.entitiesOnNodeProbe.receiveMessages(2, 15 seconds)
      messages should contain(MetricObserved(10))
  }

  it should "show proper amount of reachable nodes" in setup(TestBehavior.apply) {
    case (system, _, _, monitor, _) =>
      system.systemActorOf(ClusterSelfNodeEventsActor.apply(monitor), "sut")
      monitor.reachableNodesProbe.within(5 seconds) {
        val probe = monitor.reachableNodesProbe
        probe.receiveMessage() shouldEqual (Inc(1L))
        probe.expectNoMessage(probe.remaining)
      }
      monitor.unreachableNodesProbe.expectNoMessage()
      succeed
  }

}
