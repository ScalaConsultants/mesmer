package io.scalac.mesmer.e2e

import org.scalatest.wordspec.AnyWordSpec

class ExampleZioTest extends AnyWordSpec with E2ETest with PrometheusMetrics {

  private val zioMetrics = (Seq(
    "mesmer_zio_executor_size",
    "mesmer_zio_executor_capacity",
    "mesmer_zio_executor_concurrency",
    "mesmer_zio_executor_worker_count",
    "mesmer_zio_executor_dequeued_count",
    "mesmer_zio_executor_enqueued_count",
    "mesmer_zio_forwarded_jvm_info",
    "mesmer_zio_forwarded_zio_fiber_started",
    "mesmer_zio_forwarded_zio_fiber_successes",
    "mesmer_zio_forwarded_zio_fiber_fork_locations"
  ) ++ prometheusHistogram("mesmer_zio_forwarded_zio_fiber_lifetimes")).map("promexample_" + _)

  "ZIO example" should {
    "produce both runtime and executor metrics" in withExample("exampleZio/run") { prometheusApi =>
      zioMetrics.foreach(assertMetricExists(prometheusApi)(_))
    }
  }
}
