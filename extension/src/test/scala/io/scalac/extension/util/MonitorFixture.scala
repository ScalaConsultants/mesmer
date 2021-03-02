package io.scalac.extension.util

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
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
  type Command

  protected def createMonitor: Monitor
  protected def setUp(monitor: Monitor, cache: Boolean): ActorRef[Command]
  protected val serviceKey: Option[ServiceKey[_]] = None

  def testCaching(body: Monitor => Any): Any = internalTest((monitor, _) => body(monitor), createMonitor, cache = true)

  def test(body: Monitor => Any): Any = internalTest((monitor, _) => body(monitor), createMonitor, cache = false)

  def testWithRef(body: (Monitor, ActorRef[Command]) => Any): Any = internalTest(body, createMonitor, cache = false)

  private def internalTest(body: (Monitor, ActorRef[Command]) => Any, monitor: Monitor, cache: Boolean): Any = {
    val sut = setUp(monitor, cache)
    watch(sut)

    // TODO make mix-in infrastructure for this
    serviceKey.foreach(key => onlyRef(sut, key))
    body(monitor, sut)
    sut.unsafeUpcast[Any] ! PoisonPill
    waitFor(sut)
  }
}

trait AnyCommandMonitorFixture extends MonitorFixture {
  override type Command = Any
}
