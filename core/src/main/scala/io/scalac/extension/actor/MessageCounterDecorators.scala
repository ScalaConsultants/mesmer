package io.scalac.extension.actor

import io.scalac.core.util.CounterDecorator

object MessageCounterDecorators {

  type FieldType = CounterDecorator.FieldType

  final object Received  extends CounterDecorator("receivedMessages", "akka.actor.ActorCell")
  final object Processed extends CounterDecorator("processedMessages", "akka.actor.ActorCell")
  final object Failed    extends CounterDecorator("failedMessages", "akka.actor.ActorCell")

  @inline def setCounters(actorCell: Object): Unit = {
    Received.initialize(actorCell)
    Processed.initialize(actorCell)
    Failed.initialize(actorCell)
  }

}
