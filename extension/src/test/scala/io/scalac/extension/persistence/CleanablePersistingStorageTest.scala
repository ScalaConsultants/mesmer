package io.scalac.extension.persistence

import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningConfig
import io.scalac.extension.event.PersistenceEvent.PersistingEventStarted
import io.scalac.extension.persistence.PersistStorage.PersistEventKey
import io.scalac.extension.util.TestOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random

class CleanablePersistingStorageTest extends AnyFlatSpec with Matchers with TestOps {

  "CleanablePersistingStorage" should "clean internal buffer" in {
    val buffer        = mutable.Map.empty[PersistEventKey, PersistingEventStarted]
    val maxStaleness  = 10_000L
    val config        = CleaningConfig(maxStaleness, 10.seconds)
    val baseTimestamp = Timestamp.create()

    val staleEvents = List.fill(10) {
      val staleness = Random.nextLong(80_000) + maxStaleness
      val id        = createUniqueId
      PersistingEventStarted(s"/some/path/${id}", id, 100L, baseTimestamp.before(staleness))
    }

    val freshEvents = List.fill(10) {
      val id        = createUniqueId
      val staleness = Random.nextLong(maxStaleness)
      PersistingEventStarted(s"/some/path/${id}", id, 100L, baseTimestamp.before(staleness))
    }

    val sut = new CleanablePersistingStorage(buffer)(config) {
      override protected def currentTimestamp: Timestamp = baseTimestamp
    }

    for {
      event <- staleEvents ++ freshEvents
    } sut.persistEventStarted(event)

    sut.clean()

    buffer should have size (freshEvents.size)
    buffer.values should contain theSameElementsAs (freshEvents)
  }

}
