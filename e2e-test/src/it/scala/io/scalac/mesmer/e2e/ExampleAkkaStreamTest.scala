package io.scalac.mesmer.e2e

import org.scalatest.wordspec.AnyWordSpec

class ExampleAkkaStreamTest extends AnyWordSpec with E2ETest with PrometheusMetrics {

  private val akkaStreamMetrics = Seq(
    "mesmer_akka_streams_running_streams",
    "mesmer_akka_streams_actors",
    "mesmer_akka_stream_processed_messages",
    "mesmer_akka_streams_running_operators",
    "mesmer_akka_streams_operator_demand"
  ).map("promexample_" + _)

  "Akka Stream example" should {
    "produce stream metrics" in withExample("exampleAkkaStream/run") { prometheusApi =>
      akkaStreamMetrics.foreach(assertMetricExists(prometheusApi)(_))
    }
  }
}
