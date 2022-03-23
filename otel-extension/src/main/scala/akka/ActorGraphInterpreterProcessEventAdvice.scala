package akka

import akka.MesmerMirrorTypes.GraphInterpreterShellMirror
import akka.actor.Actor
import akka.stream.impl.fusing.ActorGraphInterpreter.BoundaryEvent
import net.bytebuddy.asm.Advice

import _root_.io.scalac.mesmer.agentcopy.akka.stream.impl.ActorGraphInterpreterOtelDecorator

object ActorGraphInterpreterProcessEventOtelAdvice {

  @Advice.OnMethodExit
  def processEvent(@Advice.This self: AnyRef, @Advice.Argument(0) boundaryEvent: BoundaryEvent): Unit =
    if (boundaryEvent.shell.isTerminated) {
      ActorGraphInterpreterOtelDecorator.shellFinished(boundaryEvent.shell, self.asInstanceOf[Actor])
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
    @Advice.Argument(0) shell: GraphInterpreterShellMirror,
    @Advice.Return initialized: Boolean
  ): Unit =
    if (!initialized) {
      ActorGraphInterpreterOtelDecorator.shellFinished(shell, self.asInstanceOf[Actor])
    }

}
