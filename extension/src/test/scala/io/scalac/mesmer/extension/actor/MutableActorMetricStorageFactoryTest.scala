package io.scalac.mesmer.extension.actor

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.util.AggMetric.LongValueAggMetric
import io.scalac.mesmer.core.util.TimeSeries

class MutableActorMetricStorageFactoryTest extends AnyFlatSpec with Matchers {

  private val factory = new MutableActorMetricStorageFactory[String]

  private def TestMetrics(value: Int) = ActorMetrics(
    Some(value),
    Some(LongValueAggMetric.fromTimeSeries(TimeSeries(1L, 1L, 10L))),
    Some(value),
    Some(value),
    Some(value),
    Some(LongValueAggMetric.fromTimeSeries(TimeSeries(1L, 1L, 10L))),
    Some(value),
    Some(value),
    Some(value)
  )

  "MutableActorMetricStorageFactory" should "have no metrics when empty" in {
    val sut = factory.createStorage

    val (buffer, persistentBuffer) = sut.buffers

    buffer should be(empty)
    persistentBuffer should be(empty)
  }

  it should "push metrics only to volatile buffer when persistent is set to false" in {
    val sut = factory.createStorage

    val expectedBuffer = Seq(
      ("a", TestMetrics(1)),
      ("b", TestMetrics(2)),
      ("c", TestMetrics(3)),
      ("d", TestMetrics(4))
    )
    expectedBuffer.foreach { case (key, value) =>
      sut.save(key, value, false)
    }

    val (buffer, persistentBuffer) = sut.buffers

    buffer should contain theSameElementsAs (expectedBuffer)
    persistentBuffer should be(empty)
  }

  it should "push metric to both buffers when persistent is set to true" in {
    val sut = factory.createStorage

    val expectedBuffer = Seq(
      ("a", TestMetrics(1)),
      ("b", TestMetrics(2)),
      ("c", TestMetrics(3)),
      ("d", TestMetrics(4))
    )
    expectedBuffer.foreach { case (key, value) =>
      sut.save(key, value, true)
    }

    val (buffer, persistentBuffer) = sut.buffers

    buffer should contain theSameElementsAs (expectedBuffer)
    persistentBuffer should contain theSameElementsAs (expectedBuffer)
  }

  it should "return only persistent metrics in iterable" in {
    val sut = factory.createStorage

    val expectedPersistentBuffer = Seq(
      ("e", TestMetrics(9)),
      ("f", TestMetrics(10))
    )
    val expectedBuffer = Seq(
      ("a", TestMetrics(1)),
      ("b", TestMetrics(2)),
      ("c", TestMetrics(3)),
      ("d", TestMetrics(4))
    )

    expectedBuffer.foreach { case (key, value) =>
      sut.save(key, value, false)
    }

    expectedPersistentBuffer.foreach { case (key, value) =>
      sut.save(key, value, true)
    }

    val (buffer, persistentBuffer) = sut.buffers

    buffer should contain theSameElementsAs (expectedBuffer ++ expectedPersistentBuffer)
    persistentBuffer should contain theSameElementsAs (expectedPersistentBuffer)

    sut.iterable should contain theSameElementsAs (expectedPersistentBuffer)
  }

  it should "consume all volatile metrics and put result in both buffer after compute" in {
    val sut = factory.createStorage

    val initialBuffer = Seq(
      ("a", TestMetrics(1)),
      ("b", TestMetrics(2)),
      ("c", TestMetrics(3)),
      ("d", TestMetrics(4))
    )

    val expectedBuffer = Seq("e" -> initialBuffer.toMap.values.reduce(_.addTo(_)))

    initialBuffer.foreach { case (key, value) =>
      sut.save(key, value, false)
    }

    sut.compute("e")

    val (buffer, persistentBuffer) = sut.buffers

    buffer should contain theSameElementsAs (expectedBuffer)
    persistentBuffer should contain theSameElementsAs (expectedBuffer)
  }

  it should "leave persistent buffer intact when computing" in {
    val sut = factory.createStorage

    val initialBuffer = Seq(
      ("a", TestMetrics(1)),
      ("b", TestMetrics(2)),
      ("c", TestMetrics(3)),
      ("d", TestMetrics(4))
    )
    val initialPersistentBuffer = Seq(
      ("e", TestMetrics(9)),
      ("f", TestMetrics(10))
    )

    val computedBuffer = Seq("g" -> (initialBuffer ++ initialPersistentBuffer).toMap.values.reduce(_.addTo(_)))
    val expectedBuffer = computedBuffer ++ initialPersistentBuffer

    initialBuffer.foreach { case (key, value) =>
      sut.save(key, value, false)
    }
    initialPersistentBuffer.foreach { case (key, value) =>
      sut.save(key, value, true)
    }

    sut.compute("g")

    val (buffer, persistentBuffer) = sut.buffers

    buffer should contain theSameElementsAs (computedBuffer)
    persistentBuffer should contain theSameElementsAs (expectedBuffer)
  }

  it should "use only data in volatile buffer to compute metrics" in {
    val sut = factory.createStorage

    val firstBatch = Seq(
      ("a", TestMetrics(1)),
      ("b", TestMetrics(2))
    )

    val secondBatch = Seq(
      ("c", TestMetrics(3)),
      ("d", TestMetrics(4))
    )

    val (buffer, persistentBuffer) = sut.buffers

    val expected =
      Seq("e" -> firstBatch.toMap.values.reduce(_.addTo(_)), "f" -> secondBatch.toMap.values.reduce(_.addTo(_)))

    firstBatch.foreach { case (key, value) =>
      sut.save(key, value, false)
    }

    sut.compute("e")

    buffer.clear() // remove computed value for volatile buffer

    secondBatch.foreach { case (key, value) =>
      sut.save(key, value, false)
    }

    sut.compute("f")

    persistentBuffer should contain theSameElementsAs (expected)
  }

  it should "override metrics in volatile buffer" in {
    val sut = factory.createStorage

    val initial = ("a", TestMetrics(1))
    val after   = ("a", TestMetrics(100))
    val common = Seq(
      ("b", TestMetrics(2)),
      ("c", TestMetrics(3)),
      ("d", TestMetrics(4))
    )

    (initial +: common :+ after).foreach { case (key, value) =>
      sut.save(key, value, false)
    }

    val (buffer, persistentBuffer) = sut.buffers

    buffer should contain theSameElementsAs (common :+ after)
    persistentBuffer should be(empty)
  }

  it should "override metrics in persistence buffer" in {
    val sut = factory.createStorage

    val initial = ("a", TestMetrics(1))
    val after   = ("a", TestMetrics(100))
    val common = Seq(
      ("b", TestMetrics(2)),
      ("c", TestMetrics(3)),
      ("d", TestMetrics(4))
    )

    (initial +: common :+ after).foreach { case (key, value) =>
      sut.save(key, value, true)
    }

    val (buffer, persistentBuffer) = sut.buffers

    persistentBuffer should contain theSameElementsAs (common :+ after)
    buffer should contain theSameElementsAs (common :+ after)
  }

  it should "override metrics in persistence buffer when computing" in {
    val sut = factory.createStorage

    val initial = ("a", TestMetrics(1))

    val common = Seq(
      ("b", TestMetrics(2)),
      ("c", TestMetrics(3)),
      ("d", TestMetrics(4))
    )

    val after = ("a", (initial +: common).toMap.values.reduce(_.addTo(_)))

    (initial +: common).foreach { case (key, value) =>
      sut.save(key, value, true)
    }

    sut.compute("a")

    val (buffer, persistentBuffer) = sut.buffers

    persistentBuffer should contain theSameElementsAs (common :+ after)
    buffer should contain theSameElementsAs (Seq(after))
  }

  it should "combine both buffers metrics" in {
    val first = factory.createStorage
    val firstMetrics = Seq(
      ("a", TestMetrics(2)),
      ("b", TestMetrics(3)),
      ("c", TestMetrics(4))
    )
    firstMetrics.foreach { case (key, value) =>
      first.save(key, value, true)
    }

    val second = factory.createStorage
    val secondMetrics = Seq(
      ("d", TestMetrics(5)),
      ("e", TestMetrics(6)),
      ("f", TestMetrics(7))
    )

    secondMetrics.foreach { case (key, value) =>
      second.save(key, value, true)
    }

    val sut = first.merge(second)

    val (buffer, persistentBuffer) = sut.buffers

    persistentBuffer should contain theSameElementsAs (firstMetrics ++ secondMetrics)
    buffer should contain theSameElementsAs (firstMetrics ++ secondMetrics)
  }

  it should "override metrics with second storage" in {
    val first       = factory.createStorage
    val firstValue  = TestMetrics(1)
    val second      = factory.createStorage
    val secondValue = TestMetrics(100)

    first.save("a", firstValue, true)
    second.save("a", secondValue, true)

    val sut = first.merge(second)

    val (buffer, persistentBuffer) = sut.buffers

    persistentBuffer should contain theSameElementsAs (Seq("a" -> secondValue))
    buffer should contain theSameElementsAs (Seq("a" -> secondValue))
  }
}
