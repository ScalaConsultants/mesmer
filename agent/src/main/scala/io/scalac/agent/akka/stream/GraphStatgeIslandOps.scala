package io.scalac.agent.akka.stream

import akka.stream.Attributes.Attribute
import akka.stream.{ Attributes, Shape, SinkShape }

object GraphStageIslandOps {

  final case object TerminalSink extends Attribute

  /**
   * Marks terminal sink in Graph island
   * Index 0 is used because traversal is reversed
   * @param mod
   * @param attributes
   * @param index
   * @return
   */
  def markLastSink(mod: Shape, attributes: Attributes, index: Int): Attributes =
    mod match {
      case SinkShape(_) if index == 0 => attributes.and(TerminalSink)
      case _                          => attributes
    }

}
