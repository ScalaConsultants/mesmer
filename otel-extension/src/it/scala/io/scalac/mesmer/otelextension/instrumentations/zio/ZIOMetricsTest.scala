package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.scalac.mesmer.agent.utils.OtelAgentTest
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import zio._
import zio.metrics.Metric

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ZIOMetricsTest extends OtelAgentTest with AnyFlatSpecLike with Matchers {

  "OTEL counter" should "be registered and working for a custom ZIO Counter" in {
    val counter = Metric.counter("my_custom_zio_counter").fromConst(1)

    val testProgram: ZIO[Any, Nothing, Unit] = (for {
      _ <- ZIO.unit @@ counter
    } yield ()).repeatN(19)

    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.runToFuture(testProgram)

      assertMetrics("mesmer_zio_forwarded_my_custom_zio_counter") {
        case data if data.getType == MetricDataType.DOUBLE_SUM =>
          val value: Option[Double] = data.getDoubleSumData.getPoints.asScala.map(_.getValue).toList.headOption
          value.get should be(20)
      }
    }
  }
}
