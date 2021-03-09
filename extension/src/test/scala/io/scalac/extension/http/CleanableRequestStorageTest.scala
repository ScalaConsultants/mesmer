package io.scalac.extension.http

import io.scalac.core.model._
import io.scalac.core.tagging._
import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningSettings
import io.scalac.extension.event.HttpEvent._
import io.scalac.extension.util.TestOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random

class CleanableRequestStorageTest extends AnyFlatSpec with Matchers with TestOps {

  "CleanableRequestStorage" should "clean internal buffer" in {
    val buffer         = mutable.Map.empty[String, RequestStarted]
    val maxStalenessMs = 10_000L
    val config         = CleaningSettings(maxStalenessMs.millis, 10.seconds)
    val baseTimestamp  = Timestamp.create()

    val staleEvents = List.fill(10) {
      val staleness = Random.nextLong(80_000) + maxStalenessMs
      val id        = createUniqueId
      RequestStarted(id, baseTimestamp.minus(staleness.millis), "/some/path".taggedWith[PathTag], "GET".taggedWith[MethodTag])
    }

    val freshEvents = List.fill(10) {
      val id        = createUniqueId
      val staleness = Random.nextLong(maxStalenessMs)
      RequestStarted(id, baseTimestamp.minus(staleness.millis), "/some/path".taggedWith[PathTag], "GET".taggedWith[MethodTag])
    }

    val sut = new CleanableRequestStorage(buffer)(config) {
      override protected def currentTimestamp: Timestamp = baseTimestamp
    }

    for {
      event <- staleEvents ++ freshEvents
    } sut.requestStarted(event)

    sut.clean()

    buffer should have size (freshEvents.size)
    buffer.values should contain theSameElementsAs (freshEvents)
  }
}
