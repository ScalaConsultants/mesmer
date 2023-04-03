package io.scalac.mesmer.e2e

import org.scalatest.wordspec.AnyWordSpec

class ExampleAkkaTest extends AnyWordSpec with E2ETest with PrometheusMetrics {

  // Akka example does not produce following metrics:
  // mesmer_akka_actor_failed_messages
  // mesmer_akka_actor_unhandled_messages
  // mesmer_akka_actor_dropped_messages
  private val akkaMetrics = (Seq(
    "mesmer_akka_actor_mailbox_size",
    "mesmer_akka_actor_stashed_messages",
    "mesmer_akka_actor_sent_messages",
    "mesmer_akka_actor_actors_created",
    "mesmer_akka_actor_actors_terminated"
  ) ++ prometheusHistogram("mesmer_akka_actor_message_processing_time") ++ prometheusHistogram(
    "mesmer_akka_actor_mailbox_time"
  )).map("promexample_" + _)

  "Akka example" should {
    "produce metrics" in withExample("exampleAkka/run", startTestString = "Starting http server at") { prometheusApi =>
      akkaMetrics.foreach(assertMetricExists(prometheusApi)(_))
    }
  }
}
