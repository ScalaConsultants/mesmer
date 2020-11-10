package io.scalac.`extension`

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.common.Labels

import scala.language.postfixOps

object LocalAgentListener {

  def apply(entities: Set[String]): Behavior[AgentListenerActorMessage] =
    Behaviors.setup { ctx =>

      ctx.system.receptionist ! Receptionist.Register(agentListenerActorKey, ctx.self)

      val meter = OpenTelemetry.getMeter("io.scalac.akka-persistence-monitoring")

      val recoveryTimeMetrics = entities
        .map(name =>
          name -> meter
            .longValueRecorderBuilder("recovery-duration")
            .setDescription("Duration of persistent actor recovery")
            .build()
            .bind(Labels.empty())
        )
        .toMap

      Behaviors.receiveMessage {

        case e @ AgentListenerActorMessage.PersistentActorRecoveryDuration(path, duration) => {
          ctx.log.debug(e.toString)
          // todo: this works only with sharding
          val entityName = path.parent.parent.name
          recoveryTimeMetrics.get(entityName).foreach(_.record(duration))
          ctx.log.info(s"Recorded persistent actor recovery time for entity '$entityName': ${duration}ms")
          Behaviors.same
        }
      }
    }
}
