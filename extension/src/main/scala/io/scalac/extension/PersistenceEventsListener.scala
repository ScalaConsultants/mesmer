package io.scalac.`extension`

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import io.scalac.extension.metric.PersistenceMetricMonitor

import scala.language.postfixOps

object PersistenceEventsListener {

  def apply(monitor: PersistenceMetricMonitor, entities: Set[String]): Behavior[AgentListenerActorMessage] =
    Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Register(persistenceService, ctx.self)

      val recoveryTimeMetrics = entities
        .map(name => name -> monitor.bind(name))
        .toMap

      Behaviors.receiveMessage {

        case e @ AgentListenerActorMessage.PersistentActorRecoveryDuration(path, duration) => {
          ctx.log.debug(e.toString)
          // todo: this works only with sharding
          val entityName = path.parent.parent.name
          recoveryTimeMetrics.get(entityName).foreach(_.recoveryTime.setValue(duration))
          ctx.log.info(s"Recorded persistent actor recovery time for entity '$entityName': ${duration}ms")
          Behaviors.same
        }
      }
    }
}
