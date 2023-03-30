package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.api.common.{ AttributeKey, Attributes }
import io.opentelemetry.sdk.metrics.data.HistogramData
import io.scalac.mesmer.agent.utils.OtelAgentTest
import io.scalac.mesmer.core.config.MesmerPatienceConfig
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import zio._
import zio.metrics.MetricKeyType.Histogram
import zio.metrics.{ Metric, MetricLabel }

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ZIOMetricsTest
    extends AnyFlatSpecLike
    with OtelAgentTest
    with Matchers
    with MesmerPatienceConfig
    with BeforeAndAfterEach {

  "plain ZIO counter" should "be picked up by our OTEL instrumentations" in {
    val counter = Metric.counter("my_custom_zio_counter").fromConst(1)

    val testProgram = for { _ <- ZIO.unit @@ counter } yield ()

    runUnsafely(testProgram, runtimeMetrics = false)

    assertCounterMetricValue("mesmer_zio_forwarded_my_custom_zio_counter", 1)
  }

  "ZIO tagged counter" should "be picked up by our OTEL instrumentations" in {
    val zioMetricName  = "my_custom_zio_counter_tagged"
    val otelMetricName = s"mesmer_zio_forwarded_$zioMetricName"
    val counter        = Metric.counter(zioMetricName).tagged(MetricLabel("foo", "bar")).fromConst(1)

    val testProgram = for { _ <- ZIO.unit @@ counter } yield ()

    runUnsafely(testProgram, runtimeMetrics = false)

    assertCounterMetricValue(otelMetricName, 1)
    assertCounterAttribute(otelMetricName, "foo", "bar")
  }

  "Runtime fiber_started metric" should "be picked up by our OTEL instrumentations" in {
    val testProgram = for {
      fiber  <- ZIO.succeed(1).fork
      fiber2 <- ZIO.succeed(1).fork
      _      <- fiber.join
      _      <- fiber2.join
    } yield ()

    runUnsafely(testProgram, runtimeMetrics = true)

    assertCounterMetricValue("mesmer_zio_forwarded_zio_fiber_started", 2)
    assertCounterMetricValue("mesmer_zio_forwarded_zio_fiber_successes", 2)
    assertHistogramData(
      "mesmer_zio_forwarded_zio_fiber_lifetimes",
      data => data.getPoints.asScala.map(_.getCount) should contain only (2)
    )
  }

  "Runtime fiber_failures metric" should "be picked up by our OTEL instrumentations" in {
    val testProgram =
      for {
        fiber  <- ZIO.fail(new Throwable("I failed.")).fork
        fiber2 <- ZIO.fail(new Throwable("I failed as well.")).fork
        _      <- fiber.join
        _      <- fiber2.join
      } yield ()

    runUnsafely(testProgram, runtimeMetrics = true)

    assertCounterMetricValue("mesmer_zio_forwarded_zio_fiber_failures", 2)
    assertHistogramData(
      "mesmer_zio_forwarded_zio_fiber_lifetimes",
      data => data.getPoints.asScala.map(_.getCount) should contain only (2)
    )
  }

  "OTEL gauge" should "be registered and working for a custom ZIO Gauge" in {
    val gauge = Metric.gauge("my_custom_zio_gauge")

    val testProgram = for { _ <- ZIO.succeed(42.0) @@ gauge } yield ()

    runUnsafely(testProgram, runtimeMetrics = false)

    assertGaugeLastMetricValue("mesmer_zio_forwarded_my_custom_zio_gauge", 42)
  }

  "OTEL histogram" should "be registered and working for a custom ZIO Histogram" in {
    val histogram = Metric.histogram("my_custom_zio_histogram", Histogram.Boundaries.linear(0, 1.0, 10))

    val testProgram = for {
      _ <- ZIO.succeed(5.0) @@ histogram
      _ =
        assertHistogramData(
          "mesmer_zio_forwarded_my_custom_zio_histogram",
          data => {
            data.getPoints.asScala.map(_.getCount) should contain only (1)
            data.getPoints.asScala.map(_.getSum) should contain only (5)
          }
        )
      _ <- ZIO.succeed(10.0) @@ histogram
      _ =
        assertHistogramData(
          "mesmer_zio_forwarded_my_custom_zio_histogram",
          data => {
            data.getPoints.asScala.map(_.getCount) should contain only (2)
            data.getPoints.asScala.map(_.getSum) should contain only (15)
          }
        )
    } yield ()

    runUnsafely(testProgram, runtimeMetrics = false)
  }

  "ZIO frequency" should "be instrumented as a counter for each value" in {
    val frequency = Metric.frequency("my_custom_zio_frequency")

    val testProgram = for {
      _ <- ZIO.succeed("hello") @@ frequency
      _ <- ZIO.succeed("hello") @@ frequency
      _ <- ZIO.succeed("world") @@ frequency
    } yield ()

    runUnsafely(testProgram, runtimeMetrics = false)

    assertCounterMetricValue("mesmer_zio_forwarded_my_custom_zio_frequency", 2, Map("bucket" -> "hello"))
    assertCounterMetricValue("mesmer_zio_forwarded_my_custom_zio_frequency", 1, Map("bucket" -> "world"))
  }

  private def runUnsafely[E <: Throwable, A](
    testProgram: ZIO[Any, E, A],
    runtimeMetrics: Boolean
  ): CancelableFuture[Any] = {
    val program = if (runtimeMetrics) testProgram.provide(Runtime.enableRuntimeMetrics) else testProgram
    Unsafe.unsafe(implicit u => Runtime.default.unsafe.runToFuture(program))
  }

  private def assertGaugeLastMetricValue(metricName: String, value: Double): Unit =
    assertMetric(metricName)(data => getGaugeValue(data).get should be(value))

  private def getGaugeValue(data: MetricData): Option[Double] =
    data.getDoubleGaugeData.getPoints.asScala.map(_.getValue).toList.lastOption

  private def assertCounterMetricValue(metricName: String, value: Double): Unit =
    assertMetric(metricName)(data => getCounterValue(data, Map.empty).get should be(value))

  private def assertCounterMetricValue(
    metricName: String,
    value: Double,
    attributes: Map[String, String]
  ): Unit =
    assertMetric(metricName)(data => getCounterValue(data, attributes).get should be(value))

  private def getCounterValue(data: MetricData, attributes: Map[String, String]): Option[Double] =
    data.getDoubleSumData.getPoints.asScala
      .filter(pointData =>
        attributes.forall { case (key, value) =>
          Option(pointData.getAttributes.get(AttributeKey.stringKey(key))).contains(value)
        }
      )
      .map(_.getValue)
      .toList
      .headOption

  private def assertCounterAttribute(metricName: String, key: String, value: String): Unit =
    assertMetric(metricName) { data =>
      val attributeKey = AttributeKey.stringKey(key)
      getCounterAttributes(data).map(_.get(attributeKey)) should be(Some(value))
    }

  private def getCounterAttributes(data: MetricData): Option[Attributes] =
    data.getDoubleSumData.getPoints.asScala.map(_.getAttributes).toList.headOption

  private def assertHistogramData(metricName: String, block: HistogramData => Unit): Unit =
    assertMetric(metricName)(data => block(getHistogramValue(data)))

  private def getHistogramValue(data: MetricData): HistogramData =
    data.getHistogramData
}
