package io.scalac.extension.event

import akka.actor.typed.receptionist.ServiceKey

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ServiceTest extends AnyFlatSpec with Matchers {
  val anyServiceKey    = ServiceKey[Any]("somekey")
  val stringServiceKey = ServiceKey[String]("otherkey")

  "Service" should "should equal if internal serviceKey equals" in {
    val serviceLeft  = Service(anyServiceKey)
    val serviceRight = Service(anyServiceKey)
    serviceLeft shouldEqual (serviceRight)
  }

  it should "equal for the same service" in {
    val service = Service(anyServiceKey)
    service shouldEqual (service)
  }

  it should "not equal for different service keys" in {
    Service(anyServiceKey) shouldNot equal(Service(stringServiceKey))
  }

  it should "not equal for different objects" in {
    Service(anyServiceKey) shouldNot equal(new Object())
  }

}
