package io.scalac.extension.util

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
import io.scalac.extension.service.PathService
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
  protected def setUp(monitor: Monitor, pathService: PathService, cache: Boolean): ActorRef[_]
  protected val serviceKey: ServiceKey[_]

  def testCaching(body: Monitor => Any): Any = internalTest((monitor, _) => body(monitor), cache = true)

  def test(body: Monitor => Any): Any = internalTest((monitor, _) => body(monitor), cache = false)

  def test(body: (Monitor, TestingPathService) => Any): Any = internalTest(body, cache = false)

  private def internalTest(body: (Monitor, TestingPathService) => Any, cache: Boolean): Any = {
    val pathService = new TestingPathService()
    val monitor     = createMonitor
    val sut         = setUp(monitor, pathService, cache)

    watch(sut)

    onlyRef(sut, serviceKey)
    body(monitor, pathService)
    sut.unsafeUpcast[Any] ! PoisonPill
    waitFor(sut)
  }
}
