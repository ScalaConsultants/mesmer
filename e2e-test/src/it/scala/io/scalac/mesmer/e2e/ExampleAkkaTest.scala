package io.scalac.mesmer.e2e

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.UUID

import org.scalatest.wordspec.AnyWordSpec

class ExampleAkkaTest extends AnyWordSpec with E2ETest with PrometheusMetrics {

  // Akka example does not produce following metrics:
  // mesmer_akka_actor_failed_messages
  // mesmer_akka_actor_unhandled_messages
  // mesmer_akka_actor_dropped_messages
  // mesmer_akka_cluster_unreachable_nodes
  private val akkaMetrics = {
    val actorMetrics = Seq(
      "mesmer_akka_actor_mailbox_size",
      "mesmer_akka_actor_stashed_messages",
      "mesmer_akka_actor_sent_messages",
      "mesmer_akka_actor_actors_created",
      "mesmer_akka_actor_actors_terminated"
    ) ++
      prometheusHistogram("mesmer_akka_actor_message_processing_time") ++
      prometheusHistogram("mesmer_akka_actor_mailbox_time")
    val persistenceMetrics = Seq("mesmer_akka_persistence_snapshot") ++
      prometheusHistogram("mesmer_akka_persistence_recovery_time") ++
      prometheusHistogram("mesmer_akka_persistence_event_time")
    val clusterMetrics = Seq(
      "mesmer_akka_cluster_node_down",
      "mesmer_akka_cluster_reachable_nodes",
      "mesmer_akka_cluster_entities_on_node",
      "mesmer_akka_cluster_shards_per_region",
      "mesmer_akka_cluster_entities_per_region",
      "mesmer_akka_cluster_shard_regions_on_node"
    )
    actorMetrics ++ persistenceMetrics ++ clusterMetrics
  }.map("promexample_" + _)

  private def exampleApiRequest(accountId: UUID) = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://localhost:8080/api/v1/account/$accountId/deposit/1.0"))
      .POST(BodyPublishers.noBody())
      .build()
    val client = HttpClient
      .newBuilder()
      .build()
    client.send(request, BodyHandlers.ofString())
  }

  "Akka example" should {
    "produce metrics" in withExample("exampleAkka/run", startTestString = "Starting http server at") { prometheusApi =>
      // to produce cluster and persistence metrics
      val accountId = UUID.randomUUID()
      // snapshots are created for every 10 events
      (1 to 10).foreach(_ => exampleApiRequest(accountId))

      akkaMetrics.foreach(assertMetricExists(prometheusApi)(_))
    }
  }
}
