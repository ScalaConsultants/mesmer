package io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl

import akka.actor.ActorRef
import akka.persistence.typed.PersistenceId
import io.opentelemetry.api.common.Attributes

import io.scalac.mesmer.core.akka.model.AttributeNames
import io.scalac.mesmer.core.util.Interval

final class PersistenceContext private[impl] (val attributes: Attributes) {
  private[this] var nanos: Long = _

  def startTimer(): Unit = nanos = System.nanoTime()
  def stopTimer(): Long  = new Interval(System.nanoTime() - nanos).toNano
}

trait PersistenceContextProvider {
  def create(ref: ActorRef, persitenceId: PersistenceId): PersistenceContext
}

/**
 * Provider that substitute persistence id with {id}
 */
final class TemplatingPersistenceContextProvider extends PersistenceContextProvider {
  def create(ref: ActorRef, persitenceId: PersistenceId): PersistenceContext = {
    val path = ref.path.toStringWithoutAddress.replace(s"/${persitenceId.id}", "/{id}")

    new PersistenceContext(
      Attributes
        .builder()
        .put(AttributeNames.EntityPath, path)
        .build()
    )
  }
}

/**
 * Only recommended for tests
 */
final class IdentityPersistenceContextProvider extends PersistenceContextProvider {
  def create(ref: ActorRef, persitenceId: PersistenceId): PersistenceContext =
    new PersistenceContext(
      Attributes
        .builder()
        .put(AttributeNames.EntityPath, ref.path.toStringWithoutAddress)
        .build()
    )
}
