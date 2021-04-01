package io.scalac.core.event

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
  sealed trait ConnectionEvent extends HttpEvent {
    def interface: String
    def port: Int
  }
  case class ConnectionStarted(interface: String, port: Int)   extends ConnectionEvent
  case class ConnectionCompleted(interface: String, port: Int) extends ConnectionEvent

  sealed trait RequestEvent extends HttpEvent {
    def id: String
  }
  case class RequestStarted(id: String, timestamp: Timestamp, path: Path, method: Method) extends RequestEvent
  case class RequestCompleted(id: String, timestamp: Timestamp, status: Status)           extends RequestEvent
  case class RequestFailed(id: String, timestamp: Timestamp)                              extends RequestEvent
}

final case class TagEvent(ref: ActorRef, tag: Tag) extends AbstractEvent {
  override type Service = TagEvent
}

sealed trait StreamEvent extends AbstractEvent {
  type Service = StreamEvent
}

object StreamEvent {
  final case class StreamInterpreterStats(ref: ActorRef, streamName: SubStreamName, shellInfo: Set[ShellInfo])
      extends StreamEvent

  /**
   * Indicating that this part of stream has collapsed
   * @param ref
   * @param streamName
   * @param shellInfo
   */
  final case class LastStreamStats(ref: ActorRef, streamName: SubStreamName, shellInfo: ShellInfo) extends StreamEvent
}
