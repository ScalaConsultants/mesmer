package io.scalac.extension.util

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.AskPattern._

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import io.scalac.extension.util.TerminationRegistry._

class TerminationRegistryTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with TestOps
    with ScalaFutures {

  "TerminationRegistry" should "watch termination of actor" in {
    val registry  = system.systemActorOf(TerminationRegistry(), createUniqueId)
    val testId    = createUniqueId
    val testActor = system.systemActorOf(TestBehavior(testId), testId)

    registry.ask[Ack](reply => Watch(testActor, Some(reply))).isReadyWithin(1 second)
    testActor.unsafeUpcast[Any] ! PoisonPill
    registry.ask[Ack](reply => WaitForTermination(testActor, reply)).isReadyWithin(1 second)
  }

  it should "fail when watch wait was initiated unwatch all was sent" in {
    val registry  = system.systemActorOf(TerminationRegistry(), createUniqueId)
    val testId    = createUniqueId
    val testActor = system.systemActorOf(TestBehavior(testId), testId)

    registry.ask[Ack](reply => Watch(testActor, Some(reply))).isReadyWithin(1 second)
    val waitForTermination = registry.ask[Ack](reply => WaitForTermination(testActor, reply))
    registry ! UnwatchAll
    waitForTermination.failed.futureValue shouldBe (UnwatchAllException)
  }
}
