package io.scalac.extension.service

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AsyncCachingPathServiceTest extends AnyFlatSpec with Matchers {

  "AsyncCachingPathService" should "has cache ratio" in {
    val sut = new AsyncCachingPathService(10)
    val testUrl = "/api/v1/account/10"
    sut.template(testUrl)
    sut.template(testUrl)
    println(sut.cachedElements)
    sut.cacheHit() should be(3)
  }
}
