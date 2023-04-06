package io.scalac.mesmer.e2e

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.UUID

import org.scalatest.wordspec.AnyWordSpec

class ExampleAkkaTest extends AnyWordSpec with E2ETest with PrometheusExporterMetrics {
  import PrometheusExporterMetrics.Metric._

  // Akka example does not produce following metrics:
  // mesmer_akka_actor_failed_messages
  // mesmer_akka_actor_unhandled_messages
  // mesmer_akka_actor_dropped_messages
  // mesmer_akka_cluster_unreachable_nodes
  private val akkaMetrics = {
    val actorMetrics = Seq(
      Gauge("mesmer_akka_actor_mailbox_size"),
      Counter("mesmer_akka_actor_stashed_messages"),
      Counter("mesmer_akka_actor_sent_messages"),
      Counter("mesmer_akka_actor_actors_created"),
      Counter("mesmer_akka_actor_actors_terminated"),
      Histogram("mesmer_akka_actor_message_processing_time"),
      Histogram("mesmer_akka_actor_mailbox_time")
    )
    val persistenceMetrics = Seq(
      Counter("mesmer_akka_persistence_snapshot"),
      Histogram("mesmer_akka_persistence_recovery_time"),
      Histogram("mesmer_akka_persistence_event_time")
    )
    val clusterMetrics = Seq(
      Counter("mesmer_akka_cluster_node_down"),
      Gauge("mesmer_akka_cluster_reachable_nodes"),
      Gauge("mesmer_akka_cluster_entities_on_node"),
      Gauge("mesmer_akka_cluster_shards_per_region"),
      Gauge("mesmer_akka_cluster_entities_per_region"),
      Gauge("mesmer_akka_cluster_shard_regions_on_node")
    )
    actorMetrics ++ persistenceMetrics ++ clusterMetrics
  }

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
    "produce metrics" in withExample(
      "exampleAkka/run",
      additionalServices = Set("postgres"),
      startTestString = "Starting http server at"
    ) { collectorApiBlock =>
      // to produce cluster and persistence metrics
      val accountId = UUID.randomUUID()
      // snapshots are created for every 10 events
      (1 to 10).foreach(_ => exampleApiRequest(accountId))

      assertMetricsExists(collectorApiBlock, "promexample")(akkaMetrics)
    }
  }
}
