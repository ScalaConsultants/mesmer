package io.scalac.mesmer.e2e

import org.scalatest.wordspec.AnyWordSpec

class ExampleZioTest extends AnyWordSpec with E2ETest with PrometheusExporterMetrics {
  import PrometheusExporterMetrics.Metric._

  private val zioMetrics = Seq(
    Gauge("mesmer_zio_executor_size"),
    Gauge("mesmer_zio_executor_capacity"),
    Gauge("mesmer_zio_executor_concurrency"),
    Gauge("mesmer_zio_executor_worker_count"),
    Gauge("mesmer_zio_executor_dequeued_count"),
    Gauge("mesmer_zio_executor_enqueued_count"),
    Gauge("mesmer_zio_forwarded_jvm_info"),
    Counter("mesmer_zio_forwarded_zio_fiber_started"),
    Counter("mesmer_zio_forwarded_zio_fiber_successes"),
    Counter("mesmer_zio_forwarded_zio_fiber_fork_locations"),
    Histogram("mesmer_zio_forwarded_zio_fiber_lifetimes")
  )

  "ZIO example" should {
    "produce both runtime and executor metrics" in withExample("exampleZio/run") { collectorApiBlock =>
      assertMetricsExists(collectorApiBlock, "promexample")(zioMetrics)
    }
  }
}
