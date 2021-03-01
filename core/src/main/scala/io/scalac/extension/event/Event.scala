package io.scalac.extension.event

import akka.actor.ActorRef
import io.scalac.core.model.Tag.SubStreamName
import io.scalac.core.model._
import io.scalac.core.util.Timestamp

sealed trait AbstractEvent { self =>
  type Service >: self.type
}

sealed trait PersistenceEvent extends AbstractEvent {
  override type Service = PersistenceEvent
}

object PersistenceEvent {
  sealed trait RecoveryEvent                                                             extends PersistenceEvent
  case class RecoveryStarted(path: String, persistenceId: String, timestamp: Timestamp)  extends RecoveryEvent
  case class RecoveryFinished(path: String, persistenceId: String, timestamp: Timestamp) extends RecoveryEvent

  sealed trait PersistEvent extends PersistenceEvent
  case class SnapshotCreated(path: String, persistenceId: String, sequenceNr: Long, timestamp: Timestamp)
      extends PersistenceEvent
  case class PersistingEventStarted(path: String, persistenceId: String, sequenceNr: Long, timestamp: Timestamp)
      extends PersistEvent
  case class PersistingEventFinished(path: String, persistenceId: String, sequenceNr: Long, timestamp: Timestamp)
      extends PersistEvent
}

sealed trait HttpEvent extends AbstractEvent {
  override type Service = HttpEvent
}

object HttpEvent {
  case class RequestStarted(id: String, timestamp: Timestamp, path: String, method: String) extends HttpEvent
  case class RequestCompleted(id: String, timestamp: Timestamp)                             extends HttpEvent
  case class RequestFailed(id: String, timestamp: Timestamp)                                extends HttpEvent
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
