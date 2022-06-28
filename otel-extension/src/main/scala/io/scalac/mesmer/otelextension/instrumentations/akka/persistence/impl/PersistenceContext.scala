package io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl

import akka.actor.ActorRef
import akka.persistence.typed.PersistenceId
import io.opentelemetry.api.common.Attributes

import io.scalac.mesmer.core.util.Interval

final class PersistenceContext private (val attributes: Attributes) {
  private[this] var nanos: Long = _

  def startTimer(): Unit = nanos = System.nanoTime()
  def stopTimer(): Long  = Interval.toMillis(System.nanoTime() - nanos)
}

object PersistenceContext {
  def create(ref: ActorRef, persitenceId: PersistenceId): PersistenceContext = {
    val path = ref.path.toStringWithoutAddress.replace(s"/${persitenceId.id}", "/{id}")

    new PersistenceContext(
      Attributes
        .builder()
        .put("path", path)
        .build()
    )
  }
}
