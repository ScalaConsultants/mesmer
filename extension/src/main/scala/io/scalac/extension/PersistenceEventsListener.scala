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
  private case class PersistEventKey(persitenceId: String, sequenceNr: Long)

  def apply(pathService: PathService, monitor: PersistenceMetricMonitor, entities: Set[String]): Behavior[Event] =
    Behaviors.setup { ctx =>
      import Event._
      Receptionist(ctx.system).ref ! Register(persistenceServiceKey, ctx.messageAdapter(PersistentEventWrapper.apply))

      val selfNodeAddress = Cluster(ctx.system).selfMember.uniqueAddress.toNode

      // this is thread unsafe mutable data structure that relies on actor model abstraction
      val cachingMonitor = caching[Labels, PersistenceMetricMonitor](monitor)
      def getMonitor(path: String, persistenceId: PersistenceId): PersistenceMetricMonitor#Bound =
        cachingMonitor.bind(Labels(selfNodeAddress, pathService.template(path), pathService.template(persistenceId)))

      def running(
        inFlightRecoveries: Map[String, RecoveryStarted],
        inFlightPersitEvents: Map[PersistEventKey, PersistingEventStarted]
      ): Behavior[Event] =
        Behaviors.receiveMessage {
          case PersistentEventWrapper(started @ RecoveryStarted(path, persistenceId, _)) => {
            ctx.log.debug("Actor {} started recovery", path)
            running(inFlightRecoveries + (persistenceId -> started), inFlightPersitEvents)
          }
          case PersistentEventWrapper(RecoveryFinished(path, persistenceId, timestamp)) => {
            inFlightRecoveries
              .get(persistenceId)
              .fold {
                ctx.log.error(s"Got recovery finished event for actor {} but no related recovery started found", path)
                Behaviors.same[Event]
              } { started =>
                val recoveryTime = timestamp - started.timestamp
                val monitor      = getMonitor(path, persistenceId)
                ctx.log.debug("Capture recovery time {}ms for path {}", recoveryTime, path)
                monitor.recoveryTime
                  .setValue(recoveryTime)
                running(inFlightRecoveries - path, inFlightPersitEvents)
              }
          }

          case PersistentEventWrapper(pes @ PersistingEventStarted(path, persistenceId, sequenceNr, _)) => {
            ctx.log.debug("Persit event initiated for actor {}/{}:{}", path, persistenceId, sequenceNr)
            running(inFlightRecoveries, inFlightPersitEvents + (PersistEventKey(persistenceId, sequenceNr) -> pes))
          }
          case PersistentEventWrapper(PersistingEventFinished(path, persistenceId, sequenceNr, timestamp)) => {
            val key = PersistEventKey(persistenceId, sequenceNr)
            inFlightPersitEvents
              .get(key)
              .fold {
                ctx.log
                  .error(s"Got persisting event finished for {} but no related initiated event found", persistenceId)
                Behaviors.same[Event]
              } { started =>
                val persistTime = timestamp - started.timestamp
                ctx.log.debug("Persited event for actor {}/{}:{} in {}ms", path, persistenceId, sequenceNr, persistTime)

                val monitor = getMonitor(path, persistenceId)
                monitor.persistentEvent.setValue(persistTime)
                monitor.persistentEventTotal.incValue(1L)
                running(inFlightRecoveries, inFlightPersitEvents - key)
              }
          }
          case _ => Behaviors.unhandled
        }
      running(Map.empty, Map.empty)
    }
}
