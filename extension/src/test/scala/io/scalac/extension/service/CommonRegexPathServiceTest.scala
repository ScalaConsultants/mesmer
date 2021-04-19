package io.scalac.extension.service

import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.core.model._
import io.scalac.extension.service.PathService.numberTemplate
import io.scalac.extension.service.PathService.uuidTemplate

trait PathServiceTest extends Matchers {
  this: AnyFlatSpec =>

  def pathService: PathService
  val testName: String

  testName should "return same string for any nonempty string with no slashes" in {
    val testUri = "alamakota"
    pathService.template(testUri) shouldEqual (testUri)
  }

  it should "mark all number in uri as numberTemplates" in {
    val testUri     = "/api/v1/2/balance/10"
    val expectedUri = s"/api/v1/${numberTemplate}/balance/${numberTemplate}"
    pathService.template(testUri) shouldEqual (expectedUri)
  }

  it should "mark all uuids in uri as numberTemplates" in {
    val testUri     = s"/api/v1/${UUID.randomUUID()}/balance/${UUID.randomUUID()}"
    val expectedUri = s"/api/v1/${uuidTemplate}/balance/${uuidTemplate}"
    pathService.template(testUri) shouldEqual (expectedUri)
  }

  it should "result empty string for empty string" in {
    val testUri = ""
    pathService.template(testUri) shouldEqual (testUri)
  }

  it should "replace all occurrences of numbers and uuids" in {
    val testUri     = s"/api/v1/${UUID.randomUUID()}/balance/10"
    val expectedUri = s"/api/v1/${uuidTemplate}/balance/${numberTemplate}"
    pathService.template(testUri) shouldEqual (expectedUri)
  }

  it should "keep correct number of slashes" in {
    val testUri     = s"/api/v1/balance/10/"
    val expectedUri = s"/api/v1/balance/${numberTemplate}/"
    pathService.template(testUri) shouldEqual (expectedUri)
  }

  it should "handle correctly double slashes in uri" in {
    val testUri     = s"/api//v1/balance//10"
    val expectedUri = s"/api//v1/balance//${numberTemplate}"
    pathService.template(testUri) shouldEqual (expectedUri)
  }

  it should "template string without slashes" in {
    val testUri     = UUID.randomUUID().toString
    val expectedUri = uuidTemplate
    pathService.template(testUri) shouldEqual (expectedUri)
  }

}

class CommonRegexPathServiceTest extends AnyFlatSpec with Matchers with PathServiceTest {

  override lazy val pathService      = CommonRegexPathService
  override lazy val testName: String = "CommonRegexPathService"

}
