package io.scalac.core.util

import scala.concurrent.duration._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CachedQueryResultTest extends AnyFlatSpec with Matchers {

  "CachedQueryResult" should "NOT re-execute query within a interval" in {
    var calls = 0
    val queryResult = CachedQueryResult.by(1.second) {
      calls += 1
    }
    queryResult.get // usage
    queryResult.get
    calls should be(1)
  }

  it should "re-execute query within a interval" in {
    var calls = 0
    val queryResult = CachedQueryResult.by(1.second) {
      calls += 1
    }
    queryResult.get
    Thread.sleep(1100)
    queryResult.get
    calls should be(2)
  }

}
