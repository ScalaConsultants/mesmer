package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.data.{ MetricData, MetricDataType }
import io.scalac.mesmer.agent.utils.OtelAgentTest
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import zio._

import java.util.concurrent.{ SynchronousQueue, ThreadPoolExecutor, TimeUnit }
import scala.jdk.CollectionConverters._

class ZIOExecutorMetricsTest extends OtelAgentTest with AnyFlatSpecLike with Matchers {

  val testProgram: ZIO[Any, Nothing, Long] = (for {
    _ <- Random.nextInt
  } yield ())
    .repeat(Schedule.forever)

  "ZIOExecutorMetrics" should "collect all execution metrics" in {
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.runToFuture(testProgram)

      assertGaugeIsCollected("mesmer_zio_executor_worker_count")
      assertGaugeIsCollected("mesmer_zio_executor_concurrency")
      assertGaugeIsCollected("mesmer_zio_executor_dequeued_count")
      assertGaugeIsCollected("mesmer_zio_executor_enqueued_count")
      assertGaugeIsCollected("mesmer_zio_executor_size")
      assertGaugeIsCollected("mesmer_zio_executor_capacity")
    }

    def assertGaugeIsCollected(name: String): Unit = assertMetrics(metricName = name) { case _ => () }
  }

  "There" should "be only one executor that has the given concurrency" in {
    val concurrency: Int             = 17
    val executor: ThreadPoolExecutor = createCustomExecutor(concurrency)

    Unsafe.unsafe { implicit u =>
      val customExecutor = Runtime.setExecutor(Executor.fromThreadPoolExecutor(executor))
      Runtime.default.unsafe.runToFuture(testProgram.provide(customExecutor))

      assertMetrics("mesmer_zio_executor_concurrency") {
        case data if data.getType == MetricDataType.LONG_GAUGE =>
          val concurrencySeries = findGaugeDataSeriesWithStaticValue(concurrency, data)
          concurrencySeries.size should be(1)
          all(concurrencySeries.values.last) should be(concurrency)
      }
    }
  }

  "Worker count metric" should "not be 0 (at least one executor has workers that are live at least for a while)" in {
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.runToFuture(testProgram)

      assertMetrics("mesmer_zio_executor_worker_count") {
        case data if data.getType == MetricDataType.LONG_GAUGE =>
          val workerCountPerExecutor = data.getLongGaugeData.getPoints.asScala.map(_.getValue)
          workerCountPerExecutor.sum should not be 0
      }
    }
  }

  "Executor's enqueued_count" should "should report a non-zero value of enqueued messages" in {
    val executor: ThreadPoolExecutor = createCustomExecutor(10)

    Unsafe.unsafe { implicit u =>
      val customExecutor = Runtime.setExecutor(Executor.fromThreadPoolExecutor(executor))
      Runtime.default.unsafe.runToFuture(testProgram.provide(customExecutor))

      assertMetrics("mesmer_zio_executor_enqueued_count") {
        case data if data.getType == MetricDataType.LONG_GAUGE =>
          val points: Iterable[Long] = data.getLongGaugeData.getPoints.asScala.map(_.getValue)
          points.sum should not be 0
      }
    }
  }

  "Executor's dequeued_count" should "should report a non-zero value of dequeued messages" in {
    val executor: ThreadPoolExecutor = createCustomExecutor(10)

    Unsafe.unsafe { implicit u =>
      val customExecutor = Runtime.setExecutor(Executor.fromThreadPoolExecutor(executor))
      Runtime.default.unsafe.runToFuture(testProgram.provide(customExecutor))

      assertMetrics("mesmer_zio_executor_dequeued_count") {
        case data if data.getType == MetricDataType.LONG_GAUGE =>
          val points: Iterable[Long] = data.getLongGaugeData.getPoints.asScala.map(_.getValue)
          points.sum should not be 0
      }
    }
  }

  private def createCustomExecutor(maxPoolSize: Int) =
    new ThreadPoolExecutor(
      10,
      maxPoolSize,
      60000L,
      TimeUnit.MILLISECONDS,
      new SynchronousQueue[Runnable](),
      (r: Runnable) => new Thread(r)
    )

  private def findGaugeDataSeriesWithStaticValue(value: Long, data: MetricData): Map[String, Iterable[Long]] =
    data.getLongGaugeData.getPoints.asScala
      .groupBy(_.getAttributes.get(AttributeKey.stringKey("executor")))
      .view
      .mapValues(_.map(_.getValue))
      .filter(_._2.toList.forall(_ == value))
      .toMap
}
