package io.scalac.extension.http

import io.scalac.extension.config.CleaningConfig
import io.scalac.extension.event.HttpEvent._
import io.scalac.extension.util.TestOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random

class CleanableRequestStorageTest extends AnyFlatSpec with Matchers with TestOps {

  "CleanableRequestStorage" should "clean internal buffer" in {
    val buffer        = mutable.Map.empty[String, RequestStarted]
    val maxStaleness  = 10_000L
    val config        = CleaningConfig(maxStaleness, 10.seconds)
    val baseTimestamp = 100_000L

    val staleEvents = List.fill(10) {
      val staleness = Random.nextLong(80_000) + maxStaleness
      val id        = createUniqueId
      RequestStarted(id, baseTimestamp - staleness, "/some/path", "GET")
    }

    val freshEvents = List.fill(10) {
      val id        = createUniqueId
      val staleness = Random.nextLong(maxStaleness)
      RequestStarted(id, baseTimestamp - staleness, "/some/path", "GET")
    }

    val sut = new CleanableRequestStorage(buffer)(config) {
      override protected def timestamp: Long = baseTimestamp
    }

    for {
      event <- staleEvents ++ freshEvents
    } sut.requestStarted(event)

    sut.clean()

    buffer should have size (freshEvents.size)
    buffer.values should contain theSameElementsAs (freshEvents)
  }
}
