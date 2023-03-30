package example

import io.circe.Json
import io.scalac.mesmer.e2e.ExampleTestHarness
import org.scalatest.EitherValues
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExampleZioTest
    extends AnyWordSpec
    with ExampleTestHarness
    with Matchers
    with Eventually
    with IntegrationPatience
    with EitherValues {

  "ZIO example" should {
    "produce both runtime and executor metrics" in withExample("exampleZio/run") { prometheusApi =>
      eventually {
        prometheusApi.assert(
          "promexample_mesmer_zio_forwarded_zio_fiber_started",
          response =>
            response.hcursor
              .downField("data")
              .downField("result")
              .as[Seq[Json]]
              .value should not be empty
        )
      }

      eventually {
        prometheusApi.assert(
          "promexample_mesmer_zio_executor_size",
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
}
