package io.scalac.mesmer.e2e

import io.circe.Json
import io.scalac.mesmer.e2e.E2ETest.PrometheusApi
import org.scalatest.EitherValues
import org.scalatest.Suite
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers

trait PrometheusMetrics extends Eventually with Matchers with EitherValues { this: Suite =>

  def prometheusHistogram(metricName: String): Seq[String] = Seq(
    "sum",
    "count",
    "bucket"
  ).map(s"${metricName}_" + _)

  def assertMetricExists(prometheusApi: PrometheusApi)(metricName: String): Unit =
    withClue(s"Metric [$metricName] should be produced") {
      eventually {
        prometheusApi.assert(
          metricName,
          response =>
            response.hcursor
              .downField("data")
              .downField("result")
              .as[Seq[Json]]
              .value should not be empty
        )
      }
    }
}
