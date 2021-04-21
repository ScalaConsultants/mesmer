package io.scalac.mesmer.core.util

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class LongNoLockAggregatorTest extends AsyncFlatSpec with Matchers {

  "LongNoLockAggregator" should "provide metrics aggregation properly" in {
    val ps     = 10
    val ns     = 10000
    val factor = 1000
    val agg    = new LongNoLockAggregator()
    val ops = for (i <- 1 to ps) yield Future {
      for (_ <- 1 to ns) agg.push(i * factor)
    }
    Future
      .sequence(ops)
      .map { _ =>
        val result = agg.fetch().get
        result.count should be(ps * ns)
        result.sum should be((for (i <- 1 to ps) yield ns * i * factor).sum)
        result.min should be(factor)
        result.max should be(ps * factor)
        result.avg should be(math.round(((ps + 1) / 2.0) * factor))
      }
  }

}
