package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.sdk.metrics.data.{ MetricData, MetricDataType }
import io.scalac.mesmer.agent.utils.OtelAgentTest
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import zio._
import zio.metrics.Metric

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ZIOMetricsTest extends OtelAgentTest with AnyFlatSpecLike with Matchers {

  "OTEL counter" should "be registered and working for a custom ZIO Counter" in {
    val counter = Metric.counter("my_custom_zio_counter").fromConst(1)

    val testProgram = (for {
      _ <- ZIO.unit @@ counter
    } yield ()).repeatN(19)

    runUnsafely(testProgram, runtimeMetrics = false)

    assertCounterMetricValue("mesmer_zio_forwarded_my_custom_zio_counter", 20)
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
  }

  "OTEL gauge" should "be registered and working for a custom ZIO Gauge" in {
    val gauge = Metric.gauge("my_custom_zio_gauge")

    val testProgram = for { _ <- ZIO.succeed(42.0) @@ gauge } yield ()

    runUnsafely(testProgram, runtimeMetrics = false)

    assertGaugeLastMetricValue("mesmer_zio_forwarded_my_custom_zio_gauge", 42)
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
    assertMetric(metricName)(data => getCounterValue(data).get should be(value))

  private def getCounterValue(data: MetricData): Option[Double] =
    data.getDoubleSumData.getPoints.asScala.map(_.getValue).toList.headOption
}
