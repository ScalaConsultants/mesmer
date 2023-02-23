package io.scalac.mesmer.core.util

import akka.actor.PoisonPill
import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import org.scalatest.Inside
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.config.MesmerPatienceConfig

trait ReceptionistOps
    extends TestOps
    with Eventually
    with Inside
    with LoneElement
    with Matchers
    with MesmerPatienceConfig {

  def killServices(serviceKey: ServiceKey[_])(implicit system: ActorSystem[_], timeout: Timeout): Unit = {
    val result = findServices(serviceKey)
    result.allServiceInstances(serviceKey).foreach(_.unsafeUpcast[Any] ! PoisonPill)
  }

  def noServices(serviceKey: ServiceKey[_])(implicit system: ActorSystem[_], timeout: Timeout): Unit =
    eventually {
      val result = findServices(serviceKey)
      result.allServiceInstances(serviceKey) should be(empty)
    }

  private def findServices(serviceKey: ServiceKey[_])(implicit system: ActorSystem[_], timeout: Timeout) =
    Receptionist(system).ref.ask[Listing](reply => Receptionist.find(serviceKey, reply)).futureValue

}
