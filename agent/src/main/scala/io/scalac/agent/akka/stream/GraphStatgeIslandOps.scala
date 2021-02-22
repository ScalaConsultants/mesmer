package io.scalac.agent.akka.stream

import akka.stream.Attributes.Attribute
import akka.stream.{ Attributes, Shape, SinkShape }

object GraphStageIslandOps {

  final case object TerminalSink extends Attribute

  def markLastSink(mod: Shape, attributes: Attributes, index: Int): Attributes =
    mod match {
      case SinkShape(_) if index == 0 => attributes.and(TerminalSink)
      case _                          => attributes
    }

}
