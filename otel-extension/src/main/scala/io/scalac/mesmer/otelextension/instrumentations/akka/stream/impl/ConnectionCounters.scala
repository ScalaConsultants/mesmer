package io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl

final class ConnectionCounters {

  private[this] var push = 0L
  private[this] var pull = 0L

  def incrementPush: Unit = push += 1

  def incrementPull: Unit = pull += 1

  def getCounters: (Long, Long) = (push, pull)

}
