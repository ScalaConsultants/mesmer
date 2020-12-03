package io.scalac.`extension`

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster
import io.scalac.extension.event.PersistenceEvent
import io.scalac.extension.event.PersistenceEvent._
import io.scalac.extension.metric.CachingMonitor._
import io.scalac.extension.metric.PersistenceMetricMonitor
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.model._
import io.scalac.extension.service.PathService

import scala.language.postfixOps

object PersistenceEventsListener {

  sealed trait Event extends SerializableMessage

  object Event {
    private[extension] final case class PersistentEventWrapper(event: PersistenceEvent) extends Event
  }

  def apply(pathService: PathService, monitor: PersistenceMetricMonitor, entities: Set[String]): Behavior[Event] =
    Behaviors.setup { ctx =>
      import Event._
      Receptionist(ctx.system).ref ! Register(persistenceServiceKey, ctx.messageAdapter(PersistentEventWrapper.apply))

      val selfNodeAddress = Cluster(ctx.system).selfMember.uniqueAddress.toNode

      // this is thread unsafe mutable data structure that relies on actor model abstraction
      val cachingMonitor = monitor.caching

      def watchRecovery(inFlightRecoveries: Map[String, RecoveryStarted]): Behavior[Event] =
        Behaviors.receiveMessage {
          case PersistentEventWrapper(started @ RecoveryStarted(path, persistenceId, _)) => {
            ctx.log.debug("Actor {} started recovery", path)
            watchRecovery(inFlightRecoveries + (persistenceId -> started))
          }
          case PersistentEventWrapper(RecoveryFinished(path, persistenceId, timestamp)) => {
            inFlightRecoveries
              .get(persistenceId)
              .fold {
                ctx.log.error(s"Got recovery finished event for actor {} but no related recovery started found", path)
                Behaviors.same[Event]
              } { started =>
                val recoveryTime = timestamp - started.timestamp
                val labels       = Labels(selfNodeAddress, pathService.template(path), pathService.template(persistenceId))
                ctx.log.debug("Capture recovery time {}ms for labels {}", recoveryTime, labels)
                cachingMonitor
                  .bind(labels)
                  .recoveryTime
                  .setValue(recoveryTime)
                watchRecovery(inFlightRecoveries - path)
              }
          }
        }
      watchRecovery(Map.empty)
    }
}
