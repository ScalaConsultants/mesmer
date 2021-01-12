package io.scalac.extension.persistence

import io.scalac.extension.event.PersistenceEvent._
import io.scalac.extension.persistence.PersistStorage.PersistEventKey

trait PersistStorage {
  def persistEventStarted(event: PersistingEventStarted): PersistStorage
  def persistEventFinished(event: PersistingEventFinished): Option[(PersistStorage, Long)]

  protected def eventToKey(event: PersistEvent): PersistEventKey = event match {
    case PersistingEventStarted(_, persistenceId, seq, _)  => PersistEventKey(persistenceId, seq)
    case PersistingEventFinished(_, persistenceId, seq, _) => PersistEventKey(persistenceId, seq)
  }

  protected def calculate(start: PersistingEventStarted, finish: PersistingEventFinished): Long =
    start.timestamp.interval(finish.timestamp)
}

object PersistStorage {
  private[persistence] case class PersistEventKey(persistenceId: String, sequenceNr: Long)
}
