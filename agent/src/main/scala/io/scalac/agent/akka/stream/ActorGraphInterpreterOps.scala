package io.scalac.agent.akka.stream

import akka.AkkaMirrorTypes._
import akka.actor.Actor
import akka.actor.typed.scaladsl.adapter._
import akka.stream.GraphLogicOps._
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.invoke.Lookup
import io.scalac.core.model._
import io.scalac.core.util.stream.subStreamNameFromActorRef
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.StreamEvent.StreamInterpreterInfo

import java.lang.invoke.MethodType._
object ActorGraphInterpreterOps extends Lookup {

  private lazy val shells = {
    val actorInterpreter = Class.forName("akka.stream.impl.fusing.ActorGraphInterpreter")
    lookup.findVirtual(actorInterpreter, "activeInterpreters", methodType(classOf[Set[GraphInterpreterShellMirror]]))
  }

  def addCollectionReceive(
    receive: Actor.Receive,
    self: Actor
  ): Actor.Receive =
    receive.orElse { case PushMetrics =>
      val subStreamName = subStreamNameFromActorRef(self.context.self)

      val currentShells = shells
        .invoke(self)
        .asInstanceOf[Set[GraphInterpreterShellMirror]]

      var terminalFound = false

      val stats = currentShells.map { shell =>
        val connections = shell.connections.dropWhile(_ eq null).map { connection =>
          val (push, pull) = ConnectionOps.getAndResetCounterValues(connection)

          val in  = connection.inOwner.stageId
          val out = connection.outOwner.stageId
          ConnectionStats(in, out, push, pull)
        }

        val stageInfo = new Array[StageInfo](shell.logics.length)

        shell.logics.foreach { logic =>
          val isTerminal =
            if (terminalFound) false
            else {
              val terminal = logic.isTerminal
              if (terminal) {
                terminalFound = true
              }
              terminal
            }
          logic.streamUniqueStageName.foreach { stageName =>
            stageInfo(logic.stageId) = StageInfo(logic.stageId, stageName, subStreamName, isTerminal)
          }
        }

        stageInfo -> connections
      }

      EventBus(self.context.system.toTyped)
        .publishEvent(StreamInterpreterInfo(self.context.self, subStreamName, stats))
    }

}
