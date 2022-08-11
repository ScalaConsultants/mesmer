package akka

import _root_.io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ActorGraphInterpreterOtelDecorator
import akka.MesmerMirrorTypes.GraphInterpreterShellMirror
import akka.actor.Actor
import akka.stream.impl.fusing.ActorGraphInterpreter.BoundaryEvent
import net.bytebuddy.asm.Advice

object ActorGraphInterpreterProcessEventOtelAdvice {

  @Advice.OnMethodExit
  def processEvent(@Advice.This self: AnyRef, @Advice.Argument(0) boundaryEvent: Object): Unit =
    if (boundaryEvent.asInstanceOf[BoundaryEvent].shell.isTerminated) {
      ActorGraphInterpreterOtelDecorator.shellFinished(
        boundaryEvent.asInstanceOf[BoundaryEvent].shell,
        self.asInstanceOf[Actor]
      )
    }

}

/**
 * Instrumentation for short living streams - part of shell initialization is it's execution If shell is terminated
 * after that it's not added to activeInterpreters
 */
object ActorGraphInterpreterTryInitOtelAdvice {

  @Advice.OnMethodExit
  def tryInit(
    @Advice.This self: AnyRef,
    @Advice.Argument(0) shell: Object,
    @Advice.Return initialized: Boolean
  ): Unit =
    if (!initialized) {
      ActorGraphInterpreterOtelDecorator.shellFinished(
        shell.asInstanceOf[GraphInterpreterShellMirror],
        self.asInstanceOf[Actor]
      )
    }

}
