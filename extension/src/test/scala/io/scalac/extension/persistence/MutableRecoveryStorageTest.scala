package io.scalac.extension.persistence

import java.util.concurrent.ConcurrentHashMap

import io.scalac.extension.event.PersistenceEvent._
import io.scalac.extension.util.TestOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

class MutableRecoveryStorageTest extends AnyFlatSpec with Matchers with TestOps {
  type Fixture = (CMap[String, RecoveryStarted], MutableRecoveryStorage)
  def test(body: Fixture => Any): Any = {
    val buffer = new ConcurrentHashMap[String, RecoveryStarted]()
    val sut    = new MutableRecoveryStorage(buffer.asScala)
    Function.untupled(body)(buffer.asScala, sut)
  }

  "MutableRecoveryStorage" should "add started events to internal buffer" in test {
    case (buffer, sut) =>
      val events = List.fill(10) {
        val id = createUniqueId
        RecoveryStarted(s"/some/path/${id}", id, System.currentTimeMillis())
      }
      events.foreach(sut.recoveryStarted)
      buffer should have size (events.size)
      buffer.values should contain theSameElementsAs (events)
  }

  it should "remove started event from internal buffer when corresponding finish event is fired" in test {
    case (buffer, sut) =>
      val events = List.fill(10) {
        val id = createUniqueId
        RecoveryStarted(s"/some/path/${id}", id, System.currentTimeMillis())
      }
      events.foreach(sut.recoveryStarted)
      val finished = events
        .take(5)
        .map(started => RecoveryFinished(started.path, started.persistenceId, started.timestamp + 100L))
      finished.foreach(sut.recoveryFinished)

      buffer should have size (events.size - finished.size)
      buffer.values should contain theSameElementsAs (events.drop(finished.size))
  }

  it should "return same storage instance with correct latency" in test {
    case (_, sut) =>
      val id              = createUniqueId
      val startTimestamp  = System.currentTimeMillis()
      val path            = s"/some/path/${id}"
      val expectedLatency = 1234L
      sut.recoveryStarted(RecoveryStarted(path, id, startTimestamp))
      val Some((resultStorage, latency)) =
        sut.recoveryFinished(RecoveryFinished(path, id, startTimestamp + expectedLatency))
      resultStorage should be theSameInstanceAs (sut)
      latency should be(expectedLatency)
  }
}
