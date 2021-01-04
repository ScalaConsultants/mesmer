package io.scalac.extension.util

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.actor.typed.scaladsl.AskPattern._
import org.scalatest.concurrent.Eventually
import org.scalatest.{ Inside, LoneElement, Suite }



trait MonitorFixture
    extends ScalaTestWithActorTestKit
    with TestOps
    with LoneElement
    with TerminationRegistryOps
    with Inside with ReceptionistOps {
  this: Suite =>
  type Monitor

  protected def createMonitor: Monitor
  protected def setUp(monitor: Monitor): ActorRef[_]
  protected val serviceKey: ServiceKey[_]

  def test(body: Monitor => Any): Any = {
    val monitor = createMonitor
    val sut     = setUp(monitor)
    watch(sut)

    onlyRef(sut, serviceKey)
    body(monitor)
    sut.unsafeUpcast[Any] ! PoisonPill
    waitFor(sut)
  }
}
