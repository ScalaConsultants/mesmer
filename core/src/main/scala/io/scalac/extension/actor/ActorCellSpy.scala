package io.scalac.extension.actor

import io.scalac.core.util._

case class ActorCellSpy(
  mailboxTime: TimerDecorator = new TimerDecorator(),
  processingTime: TimerDecorator = new TimerDecorator(),
//  messageReceiveStart: AtomicReference[Timestamp] = new AtomicReference(Timestamp.create()),
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
