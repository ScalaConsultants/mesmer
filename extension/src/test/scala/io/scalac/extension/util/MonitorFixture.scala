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
    with Inside
    with ReceptionistOps {
  this: Suite =>
  type Monitor

  protected def createMonitor: Monitor
  protected def setUp(monitor: Monitor, cache: Boolean): ActorRef[_]
  protected val serviceKey: ServiceKey[_]

  def testCaching(body: Monitor => Any): Any = internalTest(body, createMonitor, cache = true)

  def test(body: Monitor => Any): Any = internalTest(body, createMonitor, cache = false)

  private def internalTest(body: Monitor => Any, monitor: Monitor, cache: Boolean): Any = {
    val sut = setUp(monitor, cache)
    watch(sut)

    onlyRef(sut, serviceKey)
    body(monitor)
    sut.unsafeUpcast[Any] ! PoisonPill
    waitFor(sut)
  }
}
