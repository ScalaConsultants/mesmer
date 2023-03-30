package io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl

import java.lang.invoke.MethodType.methodType

import akka.ConnectionOtelOps
import akka.MesmerMirrorTypes.GraphInterpreterShellMirror
import akka.actor.Actor
import akka.actor.typed.scaladsl.adapter._
import akka.stream.GraphLogicOtelOps._

import io.scalac.mesmer.core.akka.model.PushMetrics
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.invoke.Lookup
import io.scalac.mesmer.core.model.ShellInfo
import io.scalac.mesmer.core.model.Tag.SubStreamName
import io.scalac.mesmer.core.model.stream.ConnectionStats
import io.scalac.mesmer.core.model.stream.StageInfo
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamConfig
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamEvent._
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamService.streamService
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.stream.subStreamNameFromActorRef

object ActorGraphInterpreterOtelDecorator extends Lookup {

  private lazy val shells = {
    val actorInterpreter = Class.forName("akka.stream.impl.fusing.ActorGraphInterpreter")
    lookup.findVirtual(actorInterpreter, "activeInterpreters", methodType(classOf[Set[GraphInterpreterShellMirror]]))
  }

  def addCollectionReceive(
    receive: Actor.Receive,
    self: Actor
  ): Actor.Receive = {
    val context  = self.context
    val system   = context.system
    val interval = AkkaStreamConfig.metricSnapshotCollectInterval(system)
    system.scheduler.scheduleWithFixedDelay(interval, interval, context.self, PushMetrics)(
      context.dispatcher,
      context.self
    )

    receive.orElse { case PushMetrics =>
      val subStreamName = subStreamNameFromActorRef(context.self)

      val currentShells = shells
        .invoke(self)
        .asInstanceOf[Set[GraphInterpreterShellMirror]]

      val stats = collectStats(currentShells, subStreamName)

      EventBus(system.toTyped).publishEvent(StreamInterpreterStats(context.self, subStreamName, stats))
    }
  }

  private def collectStats(shells: Set[GraphInterpreterShellMirror], subStreamName: SubStreamName): Set[ShellInfo] = {
    var terminalFound = false

    shells.map { shell =>
      val (stats, containsTerminal) = statsForShell(shell, subStreamName, terminalFound)
      terminalFound = containsTerminal
      stats
    }
  }

  def shellFinished(shell: GraphInterpreterShellMirror, self: Actor): Unit = {
    val subStreamName = subStreamNameFromActorRef(self.context.self)
    val (stats, _)    = statsForShell(shell, subStreamName, terminalFound = false)
    EventBus(self.context.system.toTyped)
      .publishEvent(LastStreamStats(self.context.self, subStreamName, stats))
  }

  private def statsForShell(
    shell: GraphInterpreterShellMirror,
    subStreamName: SubStreamName,
    terminalFound: Boolean
  ): (ShellInfo, Boolean) = {
    var localTerminalFound = terminalFound

    val connections = shell.connections.dropWhile(_ eq null).map { connection =>
      val (push, pull) = ConnectionOtelOps.getCounterValues(connection)

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
}
