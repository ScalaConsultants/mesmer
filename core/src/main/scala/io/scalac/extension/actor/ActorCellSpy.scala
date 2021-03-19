package io.scalac.extension.actor

import io.scalac.core.util._

import SpyToolKit._

case class ActorCellSpy(
  mailboxTimeAgg: TimeAggregation = new TimeAggregation(),
  processingTimeAgg: TimeAggregation = new TimeAggregation(),
  processingTimer: Timer = new Timer,
  receivedMessages: Counter = new Counter,
  processedMessages: Counter = new Counter,
  unhandledMessages: Counter = new Counter,
  sentMessages: Counter = new Counter,
  failedMessages: Counter = new Counter,
  exceptionHandledMarker: Marker = new Marker
)

object ActorCellSpy {

  val fieldName = "actorCellSpy"

  private lazy val (getter, setter) = ReflectionFieldUtils.getHandlers("akka.actor.ActorCell", fieldName)

  def initialize(actorCell: Object): Unit =
    setter.invoke(actorCell, ActorCellSpy())

  def get(actorCell: Object): Option[ActorCellSpy] =
    Option(getter.invoke(actorCell)).map(_.asInstanceOf[ActorCellSpy])

}
