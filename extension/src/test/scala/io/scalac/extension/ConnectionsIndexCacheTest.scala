package io.scalac.extension

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.core.model.Tag.StageName.StreamUniqueStageName
import io.scalac.core.model.Tag.SubStreamName
import io.scalac.core.model._
import io.scalac.extension.AkkaStreamMonitoring.ConnectionsIndexCache
import io.scalac.extension.util.TestOps

class ConnectionsIndexCacheTest extends AnyFlatSpec with Matchers with TestOps with Inspectors {

  private def genLinearFlowData(length: Int): (Array[StageInfo], Array[ConnectionStats]) = {

    val subStreamName = SubStreamName(randomString(10), "1")
    val stages = Array.tabulate(length) { id =>
      StageInfo(id, StreamUniqueStageName("map", id), subStreamName, false)
    }

    val connections = stages
      .sliding(2)
      .map { case Array(outHandler, inHander) =>
        ConnectionStats(inHander.id, outHandler.id, 0L, 0L)
      }
      .toArray

    stages -> connections
  }

  "ConnectionsIndexCache" should "generate new entries" in {
    val sut                   = ConnectionsIndexCache.empty
    val resultMap             = sut.indexCache
    val StagesSize            = 5
    val (stages, connections) = genLinearFlowData(StagesSize)

    stages.foreach(stage => sut.get(stage)(connections))

    resultMap should have size (StagesSize)
  }

  it should "reuse existing entries" in {
    val sut                   = ConnectionsIndexCache.empty
    val resultMap             = sut.indexCache
    val StagesSize            = 5
    val (stages, connections) = genLinearFlowData(StagesSize)

    stages.foreach(stage => sut.get(stage)(connections))

    val sizeSnapshot = resultMap.size

    stages.foreach(stage => sut.get(stage)(connections))

    resultMap should have size sizeSnapshot
  }

  it should "not reuse previously create entry dut to cache invalidation" in {
    val StagesSize            = 5
    val sut                   = ConnectionsIndexCache.bounded(StagesSize - 1)
    val resultMap             = sut.indexCache
    val (stages, connections) = genLinearFlowData(StagesSize)

    stages.foreach(stage => sut.get(stage)(connections))

    forAll(stages.toSeq) { stage =>
      resultMap should not contain (stage)
      sut.get(stage)(connections) // to trigger next invalidation
    }
  }

}
