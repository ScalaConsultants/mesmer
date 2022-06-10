package io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel

final class ActorCellInstrumentationState() {

  private var failedFlag = false

  def setFailed(): Unit = failedFlag = true

  def getAndResetFailed(): Boolean = {
    val failed = failedFlag
    failedFlag = false
    failed
  }
}
