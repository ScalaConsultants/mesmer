package io.scalac.mesmer.otelextension.instrumentations.akka.persistence

import scala.reflect.io.Path

import io.scalac.mesmer.core.model.PersistenceIdTag
import io.scalac.mesmer.core.tagging.@@
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.otelextension.instrumentations.akka.common.AbstractEvent

sealed trait PersistenceEvent extends AbstractEvent {
  type Service = PersistenceEvent
}

object PersistenceEvent {

  type PersistenceId = String @@ PersistenceIdTag

  sealed trait RecoveryEvent extends PersistenceEvent

  sealed trait PersistEvent extends PersistenceEvent

  case class RecoveryStarted(path: Path, persistenceId: PersistenceId, timestamp: Timestamp) extends RecoveryEvent

  case class RecoveryFinished(path: Path, persistenceId: PersistenceId, timestamp: Timestamp) extends RecoveryEvent

  case class SnapshotCreated(path: Path, persistenceId: PersistenceId, sequenceNr: Long, timestamp: Timestamp)
      extends PersistenceEvent
  case class PersistingEventStarted(path: Path, persistenceId: PersistenceId, sequenceNr: Long, timestamp: Timestamp)
      extends PersistEvent
  case class PersistingEventFinished(path: Path, persistenceId: PersistenceId, sequenceNr: Long, timestamp: Timestamp)
      extends PersistEvent
}
