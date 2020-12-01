package io.scalac.`extension`

import akka.actor.ActorPath
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster
import io.scalac.extension.event.PersistenceEvent
import io.scalac.extension.event.PersistenceEvent._
import io.scalac.extension.metric.PersistenceMetricMonitor
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.model._

import scala.language.postfixOps

object PersistenceEventsListener {

  sealed trait Event extends SerializableMessage

  object Event {
    private[extension] final case class PersistentEventWrapper(event: PersistenceEvent) extends Event
  }

  def apply(monitor: PersistenceMetricMonitor, entities: Set[String]): Behavior[Event] =
    Behaviors.setup { ctx =>
      import Event._
      Receptionist(ctx.system).ref ! Register(persistenceServiceKey, ctx.messageAdapter(PersistentEventWrapper.apply))

      val selfNodeAddress = Cluster(ctx.system).selfMember.uniqueAddress.toNode

      val recoveryTimeMetrics = entities
        .map(name => name -> monitor.bind(Labels(selfNodeAddress, name)))
        .toMap

      def watchRecovery(inFlightRecoveries: Map[ActorPath, RecoveryStarted]): Behavior[Event] =
        Behaviors.receiveMessage {
          case PersistentEventWrapper(started @ RecoveryStarted(path, _)) => {
            ctx.log.debug("Actor {} started recovery", path)
            watchRecovery(inFlightRecoveries + (path -> started))
          }
          case PersistentEventWrapper(finished @ RecoveryFinished(path, timestamp)) => {
            inFlightRecoveries
              .get(path)
              .fold {
                ctx.log.error(s"Got recovery finished event for actor {} but no related recovery started found", path)
                Behaviors.same[Event]
              } { started =>
                val recoveryTime = timestamp - started.timestamp
                ctx.log.debug("Actor {} started recovery in {} millis", path, recoveryTime)
                val entityName = path.parent.parent.name
                recoveryTimeMetrics.get(entityName).foreach(_.recoveryTime.setValue(recoveryTime))
                watchRecovery(inFlightRecoveries - path)
              }
          }
        }
      watchRecovery(Map.empty)
    }
}
