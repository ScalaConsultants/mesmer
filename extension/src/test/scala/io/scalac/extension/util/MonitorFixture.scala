package io.scalac.extension.util

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.actor.typed.scaladsl.AskPattern._
import org.scalatest.{ Inside, LoneElement, Suite }

trait MonitorFixture
    extends ScalaTestWithActorTestKit
    with TestOps
    with LoneElement
    with TerminationRegistryOps
    with Inside {
  this: Suite =>
  type Monitor

  protected def createMonitor: Monitor
  protected def setUp(monitor: Monitor): ActorRef[_]
  protected val serviceKey: ServiceKey[_]

  def test(body: Monitor => Any): Any = {
    val monitor = createMonitor
    val sut     = setUp(monitor)
    watch(sut)

    eventually {
      val result = Receptionist(system).ref.ask[Listing](reply => Receptionist.find(serviceKey, reply)).futureValue
      inside(result) {
        case serviceKey.Listing(res) => {
          val elem = res.loneElement
          elem should sameOrParent(sut)
        }
      }
    }
    body(monitor)
    sut.unsafeUpcast[Any] ! PoisonPill
    waitFor(sut)
  }
}
