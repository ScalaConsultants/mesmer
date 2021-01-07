package io.scalac.extension

import java.util.UUID

import akka.actor.testkit.typed.javadsl.FishingOutcomes
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope

import io.scalac.extension.ClusterSelfNodeEventsActor.Command.MonitorRegion
import io.scalac.extension.event.ClusterEvent.ShardingRegionInstalled
import io.scalac.extension.event.EventBus
import io.scalac.extension.util.probe.BoundTestProbe._
import io.scalac.extension.util.{ ActorFailing, FailingInterceptor, SingleNodeClusterSpec, TestBehavior }
import org.scalatest.Inspectors
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._
import scala.language.postfixOps

class ClusterSelfNodeEventsActorTest
    extends AsyncFlatSpec
    with SingleNodeClusterSpec
    with Matchers
    with Inspectors
    with ActorFailing {

  import util.TestBehavior.Command._

  "ClusterSelfNodeEventsActor" should "show proper amount of entities" in setup(TestBehavior.apply) {
    case (system, _, ref, monitor, region) =>
      system.systemActorOf(ClusterSelfNodeEventsActor.apply(monitor), "sut")

      EventBus(system).publishEvent(ShardingRegionInstalled(region))
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Create)

      val messages = monitor.entityPerRegionProbe.receiveMessages(2, 15 seconds)
      messages should contain(MetricRecorded(10))
  }

  it should "show proper amount of entities on node" in setup(TestBehavior.apply) {
    case (system, _, ref, monitor, region) =>
      system.systemActorOf(ClusterSelfNodeEventsActor.apply(monitor), "sut")

      EventBus(system).publishEvent(ShardingRegionInstalled(region))
      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Create)

      val messages = monitor.entitiesOnNodeProbe.receiveMessages(2, 15 seconds)
      messages should contain(MetricRecorded(10))
  }

  it should "show proper amount of entities on node with more than one region" in setup(TestBehavior.apply) {
    case (system, _, ref, monitor, region) =>
      system.systemActorOf(ClusterSelfNodeEventsActor.apply(monitor), "sut")

      EventBus(system).publishEvent(ShardingRegionInstalled(region))
      EventBus(system).publishEvent(ShardingRegionInstalled(UUID.randomUUID.toString))
      // TODO How to send message to the new region?

      for (i <- 0 until 10) ref ! ShardingEnvelope(s"test_$i", Create)

      val messages = monitor.entitiesOnNodeProbe.receiveMessages(2, 15 seconds)
      messages should contain(MetricRecorded(10))
  }

  it should "show proper amount of reachable nodes" in setup(TestBehavior.apply) {
    case (system, _, _, monitor, region) =>
      system.systemActorOf(ClusterSelfNodeEventsActor.apply(monitor), "sut")

      EventBus(system).publishEvent(ShardingRegionInstalled(region))

      monitor.reachableNodesProbe.within(5 seconds) {
        val probe = monitor.reachableNodesProbe
        probe.receiveMessage() shouldEqual (Inc(1L))
        probe.expectNoMessage(probe.remaining)
      }
      monitor.unreachableNodesProbe.expectNoMessage()
      succeed
  }

  it should "have same regions monitored after restart" in setup(TestBehavior.apply) {
    case (_system, member, ref, monitor, region) =>
      implicit val system: ActorSystem[Nothing] = _system

      val probe = TestProbe[ClusterSelfNodeEventsActor.Command]

      val failingBehavior = Behaviors.intercept(() => FailingInterceptor(probe.ref))(
        ClusterSelfNodeEventsActor.apply(monitor)
      )

      val testedBehavior = Behaviors
        .supervise(failingBehavior)
        .onFailure[Throwable](SupervisorStrategy.restart)

      val sut = system.systemActorOf(testedBehavior, "sut")

      EventBus(system).publishEvent(ShardingRegionInstalled(region))

      val monitoredRegions =
        probe.fishForMessage(5 seconds) {
          case _: MonitorRegion => FishingOutcomes.complete()
          case _                => FishingOutcomes.continueAndIgnore()
        }

      monitoredRegions should have size (1)
      forAll(monitoredRegions) {
        _ shouldBe MonitorRegion(region)
      }

      sut.fail()

      val monitoredRegions2 = probe.fishForMessage(5 seconds) {
        case _: MonitorRegion => FishingOutcomes.complete()
        case _                => FishingOutcomes.continueAndIgnore()
      }

      monitoredRegions2 should have size (1)
      forAll(monitoredRegions2) {
        _ shouldBe MonitorRegion(region)
      }

      succeed
  }
}
