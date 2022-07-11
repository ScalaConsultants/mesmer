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

  "ZIOExecutorMetrics" should "produce worker_count metric" in {
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.runToFuture(testProgram)

      assertGaugeIsCollected("mesmer_zio_executor_worker_count")
      assertGaugeIsCollected("mesmer_zio_executor_concurrency")
      assertGaugeIsCollected("mesmer_zio_executor_dequeued_count")
      assertGaugeIsCollected("mesmer_zio_executor_enqueued_count")
      assertGaugeIsCollected("mesmer_zio_executor_size")
      assertGaugeIsCollected("mesmer_zio_executor_capacity")
      assertGaugeIsCollected("mesmer_zio_executor_capacity")
    }

    def assertGaugeIsCollected(name: String): Unit = assertMetrics(metricName = name) { case _ => () }
  }

  "Executor's maxPoolSize" should "be discovered as mesmer_zio_executor_concurrency" in {
    val maxPoolSize                  = 7
    val executor: ThreadPoolExecutor = createCustomExecutor(0, maxPoolSize)

    Unsafe.unsafe { implicit u =>
      val customExecutor = Runtime.setExecutor(Executor.fromThreadPoolExecutor(executor))
      Runtime.default.unsafe.runToFuture(testProgram.provide(customExecutor))

      assertMetrics("mesmer_zio_executor_concurrency") {
        case data if data.getType == MetricDataType.LONG_GAUGE =>
          val customExecutorData = findGaugeDataSeriesWithStaticValue(maxPoolSize, data)
          customExecutorData.size should be(1)
      }
    }
  }

  "Executor's corePoolSize" should "be discovered as mesmer_zio_executor_worker_count" in {
    val corePoolSize                 = 2
    val executor: ThreadPoolExecutor = createCustomExecutor(corePoolSize, 10)

    Unsafe.unsafe { implicit u =>
      val customExecutor = Runtime.setExecutor(Executor.fromThreadPoolExecutor(executor))
      Runtime.default.unsafe.runToFuture(testProgram.provide(customExecutor))

      assertMetrics("mesmer_zio_executor_worker_count") {
        case data if data.getType == MetricDataType.LONG_GAUGE =>
          val customExecutorData = findGaugeDataSeriesWithStaticValue(corePoolSize, data)
          customExecutorData.size should be(1)
      }
    }
  }

  private def createCustomExecutor(corePoolSize: RuntimeFlags, maxPoolSize: RuntimeFlags) = {
    val executor: ThreadPoolExecutor = new ThreadPoolExecutor(
      corePoolSize,
      maxPoolSize,
      60000L,
      TimeUnit.MILLISECONDS,
      new SynchronousQueue[Runnable](),
      (r: Runnable) => new Thread(r)
    )
    executor
  }

  private def findGaugeDataSeriesWithStaticValue(value: RuntimeFlags, data: MetricData): Map[String, Iterable[Long]] =
    data.getLongGaugeData.getPoints.asScala
      .groupBy(_.getAttributes.get(AttributeKey.stringKey("executor")))
      .view
      .mapValues(_.map(_.getValue))
      .filter(_._2.toList.forall(_ == value))
      .toMap

}
