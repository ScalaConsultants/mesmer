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
import io.scalac.extension.persistence.{ PersistStorage, RecoveryStorage }
import io.scalac.extension.service.PathService

import scala.language.postfixOps

object PersistenceEventsActor {

  sealed trait Event extends SerializableMessage

  object Event {
    private[extension] final case class PersistentEventWrapper(event: PersistenceEvent) extends Event
  }

  def apply(
    pathService: PathService,
    initRecoveryStorage: RecoveryStorage,
    initPersistStorage: PersistStorage,
    monitor: PersistenceMetricMonitor
  ): Behavior[Event] =
    Behaviors.setup { ctx =>
      import Event._
      Receptionist(ctx.system).ref ! Register(persistenceServiceKey, ctx.messageAdapter(PersistentEventWrapper.apply))

      val selfNodeAddress = Cluster(ctx.system).selfMember.uniqueAddress.toNode

      // this is thread unsafe mutable data structure that relies on actor model abstraction
      val cachingMonitor = caching[Labels, PersistenceMetricMonitor](monitor)
      def getMonitor(path: String, persistenceId: PersistenceId): PersistenceMetricMonitor#Bound =
        cachingMonitor.bind(Labels(selfNodeAddress, pathService.template(path), pathService.template(persistenceId)))

      def running(
        recoveryStorage: RecoveryStorage,
        persistStorage: PersistStorage
      ): Behavior[Event] =
        Behaviors.receiveMessage {
          case PersistentEventWrapper(started @ RecoveryStarted(path, _, _)) => {
            ctx.log.debug("Actor {} started recovery", path)
            running(recoveryStorage.recoveryStarted(started), persistStorage)
          }
          case PersistentEventWrapper(finished @ RecoveryFinished(path, persistenceId, _)) => {
            recoveryStorage
              .recoveryFinished(finished)
              .fold {
                ctx.log.error(s"Got recovery finished event for actor {} but no related recovery started found", path)
                Behaviors.same[Event]
              } {
                case (storage, duration) => {
                  ctx.log.trace("Recovery finished in {} for {}", duration, path)
                  val monitor = getMonitor(path, persistenceId)
                  monitor.recoveryTime.setValue(duration)
                  monitor.recoveryTotal.incValue(1L)
                  running(storage, persistStorage)
                }
              }
          }

          case PersistentEventWrapper(pes @ PersistingEventStarted(path, persistenceId, sequenceNr, _)) => {
            ctx.log.trace("Persit event initiated for actor {}/{}:{}", path, persistenceId, sequenceNr)
            running(recoveryStorage, persistStorage.persistEventStarted(pes))
          }
          case PersistentEventWrapper(finished @ PersistingEventFinished(path, persistenceId, _, _)) => {
            persistStorage
              .persistEventFinished(finished)
              .fold {
                ctx.log
                  .error(s"Got persisting event finished for {} but no related initiated event found", persistenceId)
                Behaviors.same[Event]
              } {
                case (storage, duration) => {
                  val monitor = getMonitor(path, persistenceId)
                  monitor.persistentEvent.setValue(duration)
                  monitor.persistentEventTotal.incValue(1L)
                  running(recoveryStorage, storage)
                }
              }
          }
          case PersistentEventWrapper(SnapshotCreated(path, persistenceId, _, _)) => {
            val monitor = getMonitor(path, persistenceId)
            ctx.log.trace("Received snapshot created for {}", persistenceId)
            monitor.snapshot.incValue(1L)
            Behaviors.same
          }
          case _ => Behaviors.unhandled
        }

      running(initRecoveryStorage, initPersistStorage)
    }
}
