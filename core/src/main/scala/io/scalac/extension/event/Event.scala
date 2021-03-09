package io.scalac.extension.event

import akka.actor.ActorRef
import io.scalac.core.model.Tag.SubStreamName
import io.scalac.core.model._
import io.scalac.core.util.Timestamp

sealed trait AbstractEvent { self =>
  type Service >: self.type
}

sealed trait ActorEvent extends AbstractEvent {
  type Service = ActorEvent
}

object ActorEvent {
  final case class StashMeasurement(size: Int, path: Path) extends ActorEvent
}

sealed trait PersistenceEvent extends AbstractEvent {
  type Service = PersistenceEvent
}

object PersistenceEvent {
  sealed trait RecoveryEvent                                                                  extends PersistenceEvent
  case class RecoveryStarted(path: Path, persistenceId: PersistenceId, timestamp: Timestamp)  extends RecoveryEvent
  case class RecoveryFinished(path: Path, persistenceId: PersistenceId, timestamp: Timestamp) extends RecoveryEvent

  sealed trait PersistEvent extends PersistenceEvent
  case class SnapshotCreated(path: Path, persistenceId: PersistenceId, sequenceNr: Long, timestamp: Timestamp)
      extends PersistenceEvent
  case class PersistingEventStarted(path: Path, persistenceId: PersistenceId, sequenceNr: Long, timestamp: Timestamp)
      extends PersistEvent
  case class PersistingEventFinished(path: Path, persistenceId: PersistenceId, sequenceNr: Long, timestamp: Timestamp)
      extends PersistEvent
}

sealed trait HttpEvent extends AbstractEvent {
  type Service = HttpEvent
}

object HttpEvent {
  case class RequestStarted(id: String, timestamp: Timestamp, path: Path, method: Method) extends HttpEvent
  case class RequestCompleted(id: String, timestamp: Timestamp)                           extends HttpEvent
  case class RequestFailed(id: String, timestamp: Timestamp)                              extends HttpEvent
}

final case class TagEvent(ref: ActorRef, tag: Tag) extends AbstractEvent {
  override type Service = TagEvent
}

final case class ActorInterpreterStats(
  ref: ActorRef,
  streamName: SubStreamName,
  shellInfo: Set[ShellInfo]
) extends AbstractEvent {
  override type Service = ActorInterpreterStats
}
