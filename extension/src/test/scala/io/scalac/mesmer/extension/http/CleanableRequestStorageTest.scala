package io.scalac.mesmer.extension.http

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random

import io.scalac.mesmer.core.event.HttpEvent.RequestStarted
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.config.CleaningSettings

class CleanableRequestStorageTest extends AnyFlatSpec with Matchers with TestOps {

  "CleanableRequestStorage" should "clean internal buffer" in {
    val buffer         = mutable.Map.empty[String, RequestStarted]
    val maxStalenessMs = 10_000L
    val config         = CleaningSettings(maxStalenessMs.millis, 10.seconds)
    val baseTimestamp  = Timestamp.create()

    val staleEvents = List.fill(10) {
      val staleness = Random.nextLong(80_000) + maxStalenessMs
      val id        = createUniqueId
      RequestStarted(id, baseTimestamp.minus(staleness.millis), "/some/path", "GET")
    }

    val freshEvents = List.fill(10) {
      val id        = createUniqueId
      val staleness = Random.nextLong(maxStalenessMs)
      RequestStarted(id, baseTimestamp.minus(staleness.millis), "/some/path", "GET")
    }

    val sut = new CleanableRequestStorage(buffer)(config) {
      override protected def currentTimestamp: Timestamp = baseTimestamp
    }

    for {
      event <- staleEvents ++ freshEvents
    } sut.requestStarted(event)

    sut.clean()

    buffer should have size freshEvents.size
    buffer.values should contain theSameElementsAs freshEvents
  }
}
