package io.scalac.mesmer.core.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

class RetryTest extends AnyFlatSpec with Matchers {

  it should "retry 3 times then return None" in {
    var counter        = 0
    lazy val exception = new RuntimeException("Foo")
    def function() = {
      counter = counter + 1
      throw exception
    }

    Retry.retry(3, 1.millisecond)(function()) should be(Failure(exception))
    counter should be(3)
  }

  it should "succeed" in {
    def function(): Unit = ()
    Retry.retry(Int.MaxValue, 1.millisecond)(function()) should be(Success(()))
  }

  it should "succeed the second time" in {
    var counter = 0
    def function(): Int =
      if (counter == 0) {
        counter = counter + 1
        throw new RuntimeException("fail")
      } else {
        counter
      }
    Retry.retry(3, 1.millisecond)(function()) should be(Success(1))
  }
}
