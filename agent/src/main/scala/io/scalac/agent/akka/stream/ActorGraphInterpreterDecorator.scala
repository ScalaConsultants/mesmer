package io.scalac.agent.akka.stream

import java.lang.invoke.MethodType._

import akka.AkkaMirrorTypes._
import akka.actor.Actor
import akka.actor.typed.scaladsl.adapter._
import akka.stream.GraphLogicOps._

import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.invoke.Lookup
import io.scalac.core.model.Tag.SubStreamName
import io.scalac.core.model._
import io.scalac.core.util.stream.subStreamNameFromActorRef
import io.scalac.core.event.EventBus
import io.scalac.core.event.StreamEvent.{ LastStreamStats, StreamInterpreterStats }

import java.lang.invoke.MethodType._
object ActorGraphInterpreterDecorator extends Lookup {

  private lazy val shells = {
    val actorInterpreter = Class.forName("akka.stream.impl.fusing.ActorGraphInterpreter")
    lookup.findVirtual(actorInterpreter, "activeInterpreters", methodType(classOf[Set[GraphInterpreterShellMirror]]))
  }

  @inline
  private def statsForShell(
    shell: GraphInterpreterShellMirror,
    subStreamName: SubStreamName,
    terminalFound: Boolean
  ): (ShellInfo, Boolean) = {
    var localTerminalFound = terminalFound

    val connections = shell.connections.dropWhile(_ eq null).map { connection =>
      val (push, pull) = ConnectionOps.getAndResetCounterValues(connection)

      val in  = connection.inOwner.stageId
      val out = connection.outOwner.stageId
      ConnectionStats(in, out, push, pull)
    }

    val stageInfo = new Array[StageInfo](shell.logics.length)

    shell.logics.foreach { logic =>
      val isTerminal =
        if (localTerminalFound) false
        else {
          val terminal = logic.isTerminal
          if (terminal) {
            localTerminalFound = true
          }
          terminal
        }
      logic.streamUniqueStageName.foreach { stageName =>
        stageInfo(logic.stageId) = StageInfo(logic.stageId, stageName, subStreamName, isTerminal)
      }
    }

    (stageInfo -> connections, localTerminalFound)
  }

  @inline
  def collectStats(shells: Set[GraphInterpreterShellMirror], subStreamName: SubStreamName): Set[ShellInfo] = {
    var terminalFound = false

    shells.map { shell =>
      val (stats, containsTerminal) = statsForShell(shell, subStreamName, terminalFound)
      terminalFound = containsTerminal
      stats
    }
  }

  @inline
  def addCollectionReceive(
    receive: Actor.Receive,
    self: Actor
  ): Actor.Receive =
    receive.orElse { case PushMetrics =>
      val subStreamName = subStreamNameFromActorRef(self.context.self)

      val currentShells = shells
        .invoke(self)
        .asInstanceOf[Set[GraphInterpreterShellMirror]]

      val stats = collectStats(currentShells, subStreamName)

      EventBus(self.context.system.toTyped)
        .publishEvent(StreamInterpreterStats(self.context.self, subStreamName, stats))
    }

  def shellFinished(shell: GraphInterpreterShellMirror, self: Actor): Unit = {
    val subStreamName = subStreamNameFromActorRef(self.context.self)
    val (stats, _)    = statsForShell(shell, subStreamName, false)
    EventBus(self.context.system.toTyped)
      .publishEvent(LastStreamStats(self.context.self, subStreamName, stats))
  }

}
