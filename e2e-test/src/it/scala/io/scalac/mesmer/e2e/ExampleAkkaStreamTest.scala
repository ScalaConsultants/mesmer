package io.scalac.mesmer.e2e

import io.circe.Json
import org.scalatest.EitherValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

class ExampleAkkaStreamTest
    extends AnyWordSpec
    with ExampleTestHarness
    with Matchers
    with Eventually
    with EitherValues {

  implicit val patience: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(60, Seconds)),
    interval = scaled(Span(150, Millis))
  )

  "Akka Stream example" should {
    "produce stream metrics" in withExample("exampleAkkaStream/run") { prometheusApi =>
      eventually {
        prometheusApi.assert(
          "promexample_mesmer_akka_streams_running_streams",
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
