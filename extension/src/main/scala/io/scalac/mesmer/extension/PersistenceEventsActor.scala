package io.scalac.mesmer.extension

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors

import io.scalac.mesmer.core._
import io.scalac.mesmer.core.event.PersistenceEvent
import io.scalac.mesmer.core.event.PersistenceEvent._
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.extension.metric.PersistenceMetricsMonitor
import io.scalac.mesmer.extension.metric.PersistenceMetricsMonitor.Attributes
import io.scalac.mesmer.extension.persistence.PersistStorage
import io.scalac.mesmer.extension.persistence.RecoveryStorage
import io.scalac.mesmer.extension.service.PathService

object PersistenceEventsActor {

  sealed trait Event extends SerializableMessage

  object Event {
    private[extension] final case class PersistentEventWrapper(event: PersistenceEvent) extends Event
  }

  def apply(
    monitor: PersistenceMetricsMonitor,
    initRecoveryStorage: RecoveryStorage,
    initPersistStorage: PersistStorage,
    pathService: PathService,
    node: Option[Node] = None
  ): Behavior[Event] =
    Behaviors.setup { ctx =>
      import Event._
      Receptionist(ctx.system).ref ! Register(persistenceServiceKey, ctx.messageAdapter(PersistentEventWrapper.apply))

      def getMonitor(path: Path, persistenceId: PersistenceId): PersistenceMetricsMonitor.BoundMonitor = {
        val templatedPath      = pathService.template(path)
        val pathLastSlashIndex = path.lastIndexOf('/', path.length - 2)
        if (pathLastSlashIndex > 0 && path.substring(pathLastSlashIndex + 1, path.length) == persistenceId) {
          val persistentIdTemplate =
            templatedPath.substring(templatedPath.lastIndexOf('/', templatedPath.length - 2) + 1, templatedPath.length)
          monitor.bind(Attributes(node, templatedPath, persistentIdTemplate))
        } else {
          monitor.bind(Attributes(node, templatedPath, pathService.template(persistenceId)))
        }
      }

      def running(
        recoveryStorage: RecoveryStorage,
        persistStorage: PersistStorage
      ): Behavior[Event] =
        Behaviors.receiveMessage {
          case PersistentEventWrapper(started @ RecoveryStarted(path, _, _)) =>
            ctx.log.debug("Actor {} started recovery", path)
            running(recoveryStorage.recoveryStarted(started), persistStorage)
          case PersistentEventWrapper(finished @ RecoveryFinished(path, persistenceId, _)) =>
            recoveryStorage
              .recoveryFinished(finished)
              .fold {
                ctx.log.error("Got recovery finished event for actor {} but no related recovery started found", path)
                Behaviors.same[Event]
              } { case (storage, duration) =>
                ctx.log.trace("Recovery finished in {} for {}", duration, path)
                val monitor = getMonitor(path, persistenceId)
                monitor.recoveryTime.setValue(duration)
                monitor.recoveryTotal.incValue(1L)
                running(storage, persistStorage)
              }

          case PersistentEventWrapper(pes @ PersistingEventStarted(path, persistenceId, sequenceNr, _)) =>
            ctx.log.trace("Persist event initiated for actor {}/{}:{}", path, persistenceId, sequenceNr)
            running(recoveryStorage, persistStorage.persistEventStarted(pes))
          case PersistentEventWrapper(finished @ PersistingEventFinished(path, persistenceId, _, _)) =>
            persistStorage
              .persistEventFinished(finished)
              .fold {
                ctx.log
                  .error("Got persisting event finished for {} but no related initiated event found", persistenceId)
                Behaviors.same[Event]
              } { case (storage, duration) =>
                val monitor = getMonitor(path, persistenceId)
                monitor.persistentEvent.setValue(duration)
                monitor.persistentEventTotal.incValue(1L)
                running(recoveryStorage, storage)
              }
          case PersistentEventWrapper(SnapshotCreated(path, persistenceId, _, _)) =>
            val monitor = getMonitor(path, persistenceId)
            ctx.log.trace("Received snapshot created for {}", persistenceId)
            monitor.snapshot.incValue(1L)
            Behaviors.same
          case _ => Behaviors.unhandled
        }

      running(initRecoveryStorage, initPersistStorage)
    }
}
