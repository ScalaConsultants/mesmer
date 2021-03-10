package io.scalac.extension.persistence

import io.scalac.core.model._
import io.scalac.core.tagging._
import io.scalac.core.util.Timestamp
import io.scalac.extension.event.PersistenceEvent._
import io.scalac.extension.util.TestOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._

class MutableRecoveryStorageTest extends AnyFlatSpec with Matchers with TestOps {
  type Fixture = (mutable.Map[String, RecoveryStarted], MutableRecoveryStorage)
  def test(body: Fixture => Any): Any = {
    val buffer: mutable.Map[String, RecoveryStarted] = mutable.Map.empty
    val sut                                          = new MutableRecoveryStorage(buffer)
    Function.untupled(body)(buffer, sut)
  }

  "MutableRecoveryStorage" should "add started events to internal buffer" in test { case (buffer, sut) =>
    val events = List.fill(10) {
      val id = createUniqueId
      RecoveryStarted(s"/some/path/${id}".taggedWith[PathTag], id.taggedWith[PersistenceIdTag], Timestamp.create())
    }
    events.foreach(sut.recoveryStarted)
    buffer should have size (events.size)
    buffer.values should contain theSameElementsAs (events)
  }

  it should "remove started event from internal buffer when corresponding finish event is fired" in test {
    case (buffer, sut) =>
      val events = List.fill(10) {
        val id = createUniqueId
        RecoveryStarted(s"/some/path/${id}".taggedWith[PathTag], id.taggedWith[PersistenceIdTag], Timestamp.create())
      }
      events.foreach(sut.recoveryStarted)
      val finished = events
        .take(5)
        .map(started => RecoveryFinished(started.path, started.persistenceId, started.timestamp.plus(100L.millis)))
      finished.foreach(sut.recoveryFinished)

      buffer should have size (events.size - finished.size)
      buffer.values should contain theSameElementsAs (events.drop(finished.size))
  }

  it should "return same storage instance with correct latency" in test { case (_, sut) =>
    val id              = createUniqueId
    val startTimestamp  = Timestamp.create()
    val path            = s"/some/path/${id}"
    val expectedLatency = 1234L
    sut.recoveryStarted(RecoveryStarted(path.taggedWith[PathTag], id.taggedWith[PersistenceIdTag], startTimestamp))
    val Some((resultStorage, latency)) =
      sut.recoveryFinished(
        RecoveryFinished(
          path.taggedWith[PathTag],
          id.taggedWith[PersistenceIdTag],
          startTimestamp.plus(expectedLatency.millis)
        )
      )
    resultStorage should be theSameInstanceAs (sut)
    latency should be(expectedLatency)
  }
}
