package io.scalac.agent.akka.stream

import akka.actor.ActorRef
import akka.actor.testkit.typed.javadsl.FishingOutcomes
import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.receptionist.Receptionist._
import akka.stream.scaladsl._
import io.scalac.agent.utils.InstallAgent
import io.scalac.core.model.Tag
import io.scalac.extension.event.{ Service, TagEvent }
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll }

import scala.concurrent.duration._

class AkkaStreamAgentTest
    extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with Matchers
    with InstallAgent
    with BeforeAndAfterAll
    with BeforeAndAfter {

  implicit var streamService: ActorStreamRefService = _

  def actors(implicit refService: ActorStreamRefService): Seq[ActorRef] = refService.actors
  def clear(implicit refService: ActorStreamRefService): Unit           = refService.clear

  override def beforeAll(): Unit = {
    super.beforeAll() // order is important!
    streamService = new ActorStreamRefService()
  }

  after {
    clear
  }

  final class ActorStreamRefService {
    private val probe = TestProbe[TagEvent]("stream_refs")

    def actors: Seq[ActorRef] = probe
      .fishForMessage(2.seconds) {
        case TagEvent(ref, Tag.stream) => FishingOutcomes.continue()
        case _                         => FishingOutcomes.continueAndIgnore()
      }
      .map(_.ref)

    /**
     * Make sure no more actors are created
     */
    def clear: Unit = probe.expectNoMessage(2.seconds)

    system.receptionist ! Register(Service.tagService.serviceKey, probe.ref)
  }

  "AkkaStreamAgentTest" should "instrument test to publish its metrics" in {

    val ElementsSize = 100
    val elements     = Vector.tabulate(100)(identity)
    val testStream = Source(elements)
      .throttle(10, 10.seconds)
      .map(_ * 2)
      .named("mulTwo")
      .filter(_ % 2 == 0)
      .named("filterEven")
      .toMat(Sink.fold[Int, Int](0)(_ + _).named("sinkSum"))(Keep.right)
  }

  it should "propagate actor stream ref" in {}
}
