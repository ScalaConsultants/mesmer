package io.scalac.extension.persistence

import java.util.concurrent.ConcurrentHashMap

import io.scalac.extension.config.CleaningConfig
import io.scalac.extension.event.PersistenceEvent.{ RecoveryEvent, RecoveryFinished, RecoveryStarted }
import io.scalac.extension.resource.SelfCleaning

import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

trait RecoveryStorage {

  def recoveryStarted(event: RecoveryStarted): RecoveryStorage

  def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)]

  protected def eventToKey(event: RecoveryEvent): String = event match {
    case RecoveryStarted(_, persistenceId, _)  => persistenceId
    case RecoveryFinished(_, persistenceId, _) => persistenceId
  }

  protected def calculate(start: RecoveryStarted, finish: RecoveryFinished): Long = finish.timestamp - start.timestamp
}










