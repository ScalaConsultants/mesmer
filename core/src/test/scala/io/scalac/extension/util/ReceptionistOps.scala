package io.scalac.extension.util

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.actor.typed.scaladsl.AskPattern._
import org.scalatest.concurrent.Eventually
import org.scalatest.{ Inside, LoneElement, Suite }

trait ReceptionistOps extends ScalaTestWithActorTestKit with TestOps with Eventually with Inside with LoneElement {
  this: Suite =>

  /**
   * Waits until ref is only service for serviceKey
   * @param ref
   * @param serviceKey
   */
  def onlyRef(ref: ActorRef[_], serviceKey: ServiceKey[_]): Unit = eventually {
    val result = Receptionist(system).ref.ask[Listing](reply => Receptionist.find(serviceKey, reply)).futureValue
    inside(result) {
      case serviceKey.Listing(res) => {
        val elem = res.loneElement
        elem should sameOrParent(ref)
      }
    }
  }
}
