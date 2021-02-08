package io.scalac.extension.service

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CachingPathServiceTest extends AnyFlatSpec with Matchers with PathServiceTest {

//  "AsyncCachingPathService" should "has cache ratio" in {
//    val sut = new CachingPathService(10)
//    val testUrl = "/api/v1/account/10"
//    sut.template(testUrl)
//    sut.template(testUrl)
//    println(sut.cachedElements)
//    sut.cacheHit() should be(3)
//  }

  override def pathService: PathService = new CachingPathService(10)

  override lazy val testName: String = "CachingPathService"
}
