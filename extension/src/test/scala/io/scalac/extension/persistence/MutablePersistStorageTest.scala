package io.scalac.extension.persistence

import io.scalac.core.util.Timestamp
import io.scalac.extension.event.PersistenceEvent.{ PersistingEventFinished, PersistingEventStarted }
import io.scalac.extension.persistence.PersistStorage.PersistEventKey
import io.scalac.extension.util.TestOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._

class MutablePersistStorageTest extends AnyFlatSpec with Matchers with TestOps {
  type Fixture = (mutable.Map[PersistEventKey, PersistingEventStarted], MutablePersistStorage)
  def test(body: Fixture => Any): Any = {
    val buffer: mutable.Map[PersistEventKey, PersistingEventStarted] = mutable.Map.empty
    val sut                                                          = new MutablePersistStorage(buffer)
    Function.untupled(body)(buffer, sut)
  }

  "MutablePersistStorage" should "add started events to internal buffer" in test {
    case (buffer, sut) =>
      val events = List.fill(10) {
        val id = createUniqueId
        PersistingEventStarted(s"/some/path/${id}", id, 0, Timestamp.create())
      }
      events.foreach(sut.persistEventStarted)

      buffer should have size (events.size)
      buffer.values should contain theSameElementsAs (events)
  }

  it should "remove started event from internal buffer when corresponding finish event is fired" in test {
    case (buffer, sut) =>
      val events = List.fill(10) {
        val id = createUniqueId
        PersistingEventStarted(s"/some/path/${id}", id, 0, Timestamp.create())
      }
      events.foreach(sut.persistEventStarted)
      val finished = events
        .take(5)
        .map(started =>
          PersistingEventFinished(
            started.path,
            started.persistenceId,
            started.sequenceNr,
            started.timestamp.plus(1000.millis)
          )
        )

      finished.foreach(sut.persistEventFinished)

      buffer should have size (events.size - finished.size)
      buffer.values should contain theSameElementsAs (events.drop(5))
  }

  it should "return same storage instance with correct latency" in test {
    case (_, sut) =>
      val id              = createUniqueId
      val startTimestamp  = Timestamp.create()
      val path            = s"/some/path/${id}"
      val seqNo           = 199
      val expectedLatency = 1234L
      sut.persistEventStarted(PersistingEventStarted(path, id, seqNo, startTimestamp))
      val Some((resultStorage, latency)) =
        sut.persistEventFinished(PersistingEventFinished(path, id, seqNo, startTimestamp.plus(expectedLatency.millis)))
      resultStorage should be theSameInstanceAs (sut)
      latency should be(expectedLatency)
  }
}
