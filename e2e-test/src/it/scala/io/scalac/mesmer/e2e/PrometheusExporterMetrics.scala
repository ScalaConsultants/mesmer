package io.scalac.mesmer.e2e

import io.scalac.mesmer.e2e.E2ETest.OpenTelemetryCollectorApi
import org.scalatest.EitherValues
import org.scalatest.Suite
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

trait PrometheusExporterMetrics extends Eventually with Matchers with EitherValues { this: Suite =>
  import PrometheusExporterMetrics._

  def assertMetricsExists(collectorApi: OpenTelemetryCollectorApi, metricNamePrefix: String)(
    metrics: Seq[Metric]
  ): Unit =
    eventually {
      collectorApi.assertMetrics { response =>
        val expected = metrics.map {
          case Metric.Counter(name) =>
            s"# TYPE ${metricNamePrefix}_$name counter"
          case Metric.Gauge(name) =>
            s"# TYPE ${metricNamePrefix}_$name gauge"
          case Metric.Histogram(name) =>
            s"# TYPE ${metricNamePrefix}_$name histogram"
        }

        val actual = response
          .lines()
          .iterator()
          .asScala
          .filter(_.startsWith("# TYPE"))
          .toSeq

        val result = expected.diff(actual)

        if (result.nonEmpty) {
          fail(
            s"Prometheus exporter missing expected metrics - [${result.mkString("[", ",", "]")}], \nexpected - ${expected
                .mkString("[", ",", "]")}, \nactual - ${actual.mkString("[", ",", "]")}"
          )
        }
      }
    }

}

object PrometheusExporterMetrics {
  sealed trait Metric {
    val name: String
  }
  object Metric {
    case class Counter(name: String)   extends Metric
    case class Gauge(name: String)     extends Metric
    case class Histogram(name: String) extends Metric
  }
}
