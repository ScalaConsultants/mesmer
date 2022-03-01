package io.scalac.mesmer.core.event

import akka.actor.ActorRef

import io.scalac.mesmer.core.model.Tag.SubStreamName
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.Timestamp

sealed trait AbstractEvent extends Any { self =>
  type Service >: self.type
}

sealed trait DispatcherEvent extends AbstractEvent {
  type Service = DispatcherEvent
}

object DispatcherEvent {
  sealed trait ExecutorConfigEvent {
    val minThreads: Int
    val maxThreads: Int
    val parallelismFactor: Double
  }
  case class SetForkJoinExecutorConfig(minThreads: Int, maxThreads: Int, parallelismFactor: Double)
      extends ExecutorConfigEvent
  case class SetThreadPoolExecutorConfig(minThreads: Int, maxThreads: Int, parallelismFactor: Double)
      extends ExecutorConfigEvent
}

sealed trait ActorEvent extends Any with AbstractEvent {
  type Service = ActorEvent
}

object ActorEvent {

  // Actor termination will be extracted with watching facility
  final case class ActorCreated(details: ActorRefTags) extends AnyVal with ActorEvent
  final case class TagsSet(details: ActorRefTags)      extends AnyVal with ActorEvent
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
