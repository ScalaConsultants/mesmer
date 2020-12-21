package io.scalac.extension.event

sealed trait AbstractEvent { self =>
  type Service >: self.type
}

sealed trait PersistenceEvent extends AbstractEvent {
  override type Service = PersistenceEvent
}

object PersistenceEvent {
  sealed trait RecoveryEvent                                                        extends PersistenceEvent
  case class RecoveryStarted(path: String, persistenceId: String, timestamp: Long)  extends RecoveryEvent
  case class RecoveryFinished(path: String, persistenceId: String, timestamp: Long) extends RecoveryEvent

  sealed trait PersistEvent extends PersistenceEvent
  case class SnapshotCreated(path: String, persistenceId: String, sequenceNr: Long, timestamp: Long)
      extends PersistenceEvent
  case class PersistingEventStarted(path: String, persistenceId: String, sequenceNr: Long, timestamp: Long)
      extends PersistEvent
  case class PersistingEventFinished(path: String, persistenceId: String, sequenceNr: Long, timestamp: Long)
      extends PersistEvent
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
