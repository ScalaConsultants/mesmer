package io.scalac.mesmer.e2e

import io.circe.Json
import org.scalatest.EitherValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExampleAkkaTest extends AnyWordSpec with ExampleTestHarness with Matchers with Eventually with EitherValues {

  "Akka example" should {
    "produce metrics" in withExample("exampleAkka/run", startTestString = "Starting http server at") { prometheusApi =>
      eventually {
        prometheusApi.assert(
          "promexample_mesmer_akka_actor_mailbox_size",
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
