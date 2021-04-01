package io.scalac.extension.service

import io.scalac.core.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class CommonRegexPathServiceTest extends AnyFlatSpec with Matchers {
  import CommonRegexPathService._

  "CommonRegexPathServic" should "return same string for any nonempty string with no slashes" in {
    val testUri = "alamakota"
    template(testUri) shouldEqual (testUri)
  }

  it should "mark all number in uri as numberTemplates" in {
    val testUri     = "/api/v1/2/balance/10"
    val expectedUri = s"/api/v1/${numberTemplate}/balance/${numberTemplate}"
    template(testUri) shouldEqual (expectedUri)
  }

  it should "mark all uuids in uri as numberTemplates" in {
    val testUri     = s"/api/v1/${UUID.randomUUID()}/balance/${UUID.randomUUID()}"
    val expectedUri = s"/api/v1/${uuidTemplate}/balance/${uuidTemplate}"
    template(testUri) shouldEqual (expectedUri)
  }

  it should "result empty string for empty string" in {
    val testUri = ""
    template(testUri) shouldEqual (testUri)
  }

  it should "replace all occurrences of numbers and uuids" in {
    val testUri     = s"/api/v1/${UUID.randomUUID()}/balance/10"
    val expectedUri = s"/api/v1/${uuidTemplate}/balance/${numberTemplate}"
    template(testUri) shouldEqual (expectedUri)
  }

  it should "keep correct number of slashes" in {
    val testUri     = s"/api/v1/balance/10/"
    val expectedUri = s"/api/v1/balance/${numberTemplate}/"
    template(testUri) shouldEqual (expectedUri)
  }

  it should "handle correctly double slashes in uri" in {
    val testUri     = s"/api//v1/balance//10"
    val expectedUri = s"/api//v1/balance//${numberTemplate}"
    template(testUri) shouldEqual (expectedUri)
  }

  it should "template string without slashes" in {
    val testUri     = UUID.randomUUID().toString
    val expectedUri = uuidTemplate
    template(testUri) shouldEqual (expectedUri)
  }
}
