package io.scalac.mesmer.extension.persistence

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random

import io.scalac.mesmer.core.event.PersistenceEvent.PersistingEventStarted
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.config.CleaningSettings
import io.scalac.mesmer.extension.persistence.PersistStorage.PersistEventKey

class CleanablePersistingStorageTest extends AnyFlatSpec with Matchers with TestOps {

  "CleanablePersistingStorage" should "clean internal buffer" in {
    val buffer         = mutable.Map.empty[PersistEventKey, PersistingEventStarted]
    val maxStalenessMs = 10_000L
    val config         = CleaningSettings(maxStalenessMs.millis, 10.seconds)
    val baseTimestamp  = Timestamp.create()

    val staleEvents = List.fill(10) {
      val staleness = Random.nextLong(80_000) + maxStalenessMs
      val id        = createUniqueId
      PersistingEventStarted(s"/some/path/$id", id, 100L, baseTimestamp.minus(staleness.millis))
    }

    val freshEvents = List.fill(10) {
      val id        = createUniqueId
      val staleness = Random.nextLong(maxStalenessMs)
      PersistingEventStarted(s"/some/path/$id", id, 100L, baseTimestamp.minus(staleness.millis))
    }

    val sut = new CleanablePersistingStorage(buffer)(config) {
      override protected def currentTimestamp: Timestamp = baseTimestamp
    }

    for {
      event <- staleEvents ++ freshEvents
    } sut.persistEventStarted(event)

    sut.clean()

    buffer should have size freshEvents.size
    buffer.values should contain theSameElementsAs freshEvents
  }

}
