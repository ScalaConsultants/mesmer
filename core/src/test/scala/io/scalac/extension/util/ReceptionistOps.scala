package io.scalac.extension.util

import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.{ Inside, LoneElement, Suite }

trait ReceptionistOps extends TestOps with Eventually with Inside with LoneElement {

  def onlyRef(ref: ActorRef[_], serviceKey: ServiceKey[_])(implicit system: ActorSystem[_], timeout: Timeout): Unit =
    eventually {
      val result = Receptionist(system).ref.ask[Listing](reply => Receptionist.find(serviceKey, reply)).futureValue
      inside(result) {
        case serviceKey.Listing(res) =>
          val elem = res.loneElement
          elem should sameOrParent(ref)
      }
    }

}
