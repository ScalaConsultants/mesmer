package io.scalac.mesmer.extension.persistence

import io.scalac.mesmer.core.event.PersistenceEvent._
import io.scalac.mesmer.extension.persistence.PersistStorage.PersistEventKey

trait PersistStorage {
  def persistEventStarted(event: PersistingEventStarted): PersistStorage
  def persistEventFinished(event: PersistingEventFinished): Option[(PersistStorage, Long)]

  protected def eventToKey(event: PersistEvent): PersistEventKey = event match {
    case PersistingEventStarted(_, persistenceId, seq, _)  => PersistEventKey(persistenceId, seq)
    case PersistingEventFinished(_, persistenceId, seq, _) => PersistEventKey(persistenceId, seq)
  }

  protected def calculate(start: PersistingEventStarted, finish: PersistingEventFinished): Long =
    start.timestamp.interval(finish.timestamp).toMillis
}

object PersistStorage {
  private[persistence] case class PersistEventKey(persistenceId: String, sequenceNr: Long)
}
