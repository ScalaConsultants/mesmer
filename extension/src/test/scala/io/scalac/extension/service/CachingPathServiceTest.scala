package io.scalac.extension.service

import io.scalac.extension.config.CachingConfig
import io.scalac.extension.util.TestOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CachingPathServiceTest extends AnyFlatSpec with Matchers with PathServiceTest with TestOps {

  private val cacheMax = 10

  override def pathService: CachingPathService = new CachingPathService(CachingConfig(10))

  override lazy val testName: String = "CachingPathService"

  it should "cache static element" in {
    val sut     = pathService
    val testUrl = "/api/v1/account/10"
    sut.template(testUrl)
    sut.cache.size should be(3)
  }

  it should "has limited cache size" in {
    val sut = pathService

    val testUrl = LazyList
      .continually(randomString(10))
      .distinct
      .take(cacheMax * 2)
      .mkString("/", "/", "")

    sut.template(testUrl)

    sut.cache.size should be(cacheMax)
  }
}
