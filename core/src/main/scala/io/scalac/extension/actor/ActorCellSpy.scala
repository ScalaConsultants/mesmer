package io.scalac.extension.actor

import io.scalac.core.util._

case class ActorCellSpy(
  mailboxTimeAgg: TimeAggregationDecorator = new TimeAggregationDecorator(),
  processingTimeAgg: TimeAggregationDecorator = new TimeAggregationDecorator(),
  processingTimer: TimerDecorator = new TimerDecorator,
  receivedMessages: CounterDecorator = new CounterDecorator,
  processedMessages: CounterDecorator = new CounterDecorator,
  unhandledMessages: CounterDecorator = new CounterDecorator,
  sentMessages: CounterDecorator = new CounterDecorator,
  failedMessages: CounterDecorator = new CounterDecorator,
  exceptionHandledMarker: MarkerDecorator = new MarkerDecorator
)

object ActorCellSpy {

  val fieldName = "actorCellSpy"

  private lazy val (getter, setter) = ReflectionFieldUtils.getHandlers("akka.actor.ActorCell", fieldName)

  def initialize(actorCell: Object): Unit =
    setter.invoke(actorCell, ActorCellSpy())

  def get(actorCell: Object): Option[ActorCellSpy] =
    Option(getter.invoke(actorCell)).map(_.asInstanceOf[ActorCellSpy])

}
