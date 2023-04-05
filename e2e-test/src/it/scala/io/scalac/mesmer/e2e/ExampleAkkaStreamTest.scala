package io.scalac.mesmer.e2e

import org.scalatest.wordspec.AnyWordSpec

class ExampleAkkaStreamTest extends AnyWordSpec with E2ETest with PrometheusExporterMetrics {
  import PrometheusExporterMetrics.Metric._

  private val akkaStreamMetrics = Seq(
    Gauge("mesmer_akka_streams_running_streams"),
    Gauge("mesmer_akka_streams_actors"),
    Counter("mesmer_akka_stream_processed_messages"),
    Counter("mesmer_akka_streams_running_operators"),
    Counter("mesmer_akka_streams_operator_demand")
  )

  "Akka Stream example" should {
    "produce stream metrics" in withExample("exampleAkkaStream/run") { collectorApiBlock =>
      assertMetricsExists(collectorApiBlock, "promexample")(akkaStreamMetrics)
    }
  }
}
