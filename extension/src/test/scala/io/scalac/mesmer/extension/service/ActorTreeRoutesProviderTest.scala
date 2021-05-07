package io.scalac.mesmer.extension.service

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.flatspec.{AnyFlatSpec, AnyFlatSpecLike}
import org.scalatest.matchers.should.Matchers

class ActorTreeRoutesProviderTest extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  "ActorTreeRoutesProvider" should "publish actor tree when service is available" in {

//    val sut = new ActorTreeRoutesProvider()

  }

  it should "respond with 503 when actor service is unavailable" in {

  }
}
