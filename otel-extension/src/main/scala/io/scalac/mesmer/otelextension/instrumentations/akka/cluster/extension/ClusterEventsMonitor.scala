package io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.MemberDowned
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.model.AkkaNodeOps
import io.scalac.mesmer.otelextension.instrumentations.akka.common.SerializableMessage

object ClusterEventsMonitor extends ClusterMonitorActor {

  final case class MemberEventWrapper(event: MemberEvent) extends SerializableMessage

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  private val nodesDownCounter: LongCounter = meter
    .counterBuilder("mesmer_akka_cluster_node_down")
    .setDescription("Counter for node down events")
    .build()

  override def apply(): Behavior[MemberEventWrapper] =
    OnClusterStartup { selfMember =>
      Behaviors.setup { context =>
        val reachabilityAdapter = context.messageAdapter[MemberEvent](MemberEventWrapper.apply)

        Cluster(context.system).subscriptions ! Subscribe(
          reachabilityAdapter,
          classOf[MemberEvent]
        )

        val attributes: Attributes = Attributes.builder().put("node", selfMember.uniqueAddress.toNode).build()
        nodesDownCounter.add(0L, attributes)

        Behaviors.receiveMessage { case MemberEventWrapper(event) =>
          event match {
            case MemberDowned(_) =>
              nodesDownCounter.add(1L, attributes)
            case _ =>
          }
          Behaviors.same
        }
      }
    }
}
