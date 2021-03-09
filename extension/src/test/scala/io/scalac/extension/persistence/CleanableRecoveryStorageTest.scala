package io.scalac.extension.persistence

import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningSettings
import io.scalac.extension.event.PersistenceEvent.RecoveryStarted
import io.scalac.extension.util.TestOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random

class CleanableRecoveryStorageTest extends AnyFlatSpec with Matchers with TestOps {

  "CleanableRecoveryStorage" should "clean internal buffer" in {
    val buffer         = mutable.Map.empty[String, RecoveryStarted]
    val maxStalenessMs = 10_000L
    val config         = CleaningSettings(maxStalenessMs.millis, 10.seconds)
    val baseTimestamp  = Timestamp.create()

    val staleEvents = List.fill(10) {
      val staleness = Random.nextLong(80_000) + maxStalenessMs
      val id        = createUniqueId
      RecoveryStarted(s"/some/path/${id}", id, baseTimestamp.minus(staleness.millis))
    }

    val freshEvents = List.fill(10) {
      val id        = createUniqueId
      val staleness = Random.nextLong(maxStalenessMs)
      RecoveryStarted(s"/some/path/${id}", id, baseTimestamp.minus(staleness.millis))
    }

    val sut = new CleanableRecoveryStorage(buffer)(config) {
      override protected def currentTimestamp: Timestamp = baseTimestamp
    }

    for {
      event <- staleEvents ++ freshEvents
    } sut.recoveryStarted(event)

    sut.clean()

    buffer should have size (freshEvents.size)
    buffer.values should contain theSameElementsAs (freshEvents)
  }
}
