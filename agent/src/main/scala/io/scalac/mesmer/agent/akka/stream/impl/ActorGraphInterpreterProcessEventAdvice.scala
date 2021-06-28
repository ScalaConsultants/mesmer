package akka.stream.impl.fusing

import akka.AkkaMirrorTypes.GraphInterpreterShellMirror
import akka.actor.Actor
import akka.stream.impl.fusing.ActorGraphInterpreter.BoundaryEvent
import net.bytebuddy.asm.Advice

import io.scalac.mesmer.agent.akka.stream.impl.ActorGraphInterpreterDecorator

object ActorGraphInterpreterProcessEventAdvice {

  @Advice.OnMethodExit
  def processEvent(@Advice.This self: AnyRef, @Advice.Argument(0) boundaryEvent: BoundaryEvent): Unit =
    if (boundaryEvent.shell.isTerminated) {
      ActorGraphInterpreterDecorator.shellFinished(boundaryEvent.shell, self.asInstanceOf[Actor])
    }

}

/**
 * Instrumentation for short living streams - part of shell initialization is it's execution
 * If shell is terminated after that it's not added to activeInterpreters
 */
object ActorGraphInterpreterTryInitAdvice {

  @Advice.OnMethodExit
  def tryInit(
    @Advice.This self: AnyRef,
    @Advice.Argument(0) shell: GraphInterpreterShellMirror,
    @Advice.Return initialized: Boolean
  ): Unit =
    if (!initialized) {
      ActorGraphInterpreterDecorator.shellFinished(shell, self.asInstanceOf[Actor])
    }

}
