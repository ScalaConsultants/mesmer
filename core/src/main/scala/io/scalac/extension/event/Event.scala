package io.scalac.extension.event

sealed trait AbstractEvent { self =>
  type Service >: self.type
}

sealed trait PersistenceEvent extends AbstractEvent {
  override type Service = PersistenceEvent
}

object PersistenceEvent {
  case class RecoveryStarted(path: String, persistenceId: String, timestamp: Long)  extends PersistenceEvent
  case class RecoveryFinished(path: String, persistenceId: String, timestamp: Long) extends PersistenceEvent

  case class SnapshotCreated(persistenceId: String, sequenceNr: Long, timestamp: Long) extends PersistenceEvent
  case class PersistingEventStarted(path: String, persistenceId: String, sequenceNr: Long, timestamp: Long)
      extends PersistenceEvent
  case class PersistingEventFinished(path: String, persistenceId: String, sequenceNr: Long, timestamp: Long)
      extends PersistenceEvent
}

sealed trait HttpEvent extends AbstractEvent {
  override type Service = HttpEvent
}

object HttpEvent {
  case class RequestStarted(id: String, timestamp: Long, path: String, method: String) extends HttpEvent
  case class RequestCompleted(id: String, timestamp: Long)                             extends HttpEvent
  case class RequestFailed(id: String, timestamp: Long)                                extends HttpEvent
}

sealed trait ClusterEvent extends AbstractEvent {
  override type Service = ClusterEvent
}

object ClusterEvent {
  final case class ShardingRegionInstalled(region: String) extends ClusterEvent
}
