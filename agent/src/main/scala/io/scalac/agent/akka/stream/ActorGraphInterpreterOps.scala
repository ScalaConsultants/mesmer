package io.scalac.agent.akka.stream

import akka.AkkaMirrorTypes._
import akka.actor.Actor
import akka.stream.GraphLogicOps._
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.invoke.Lookup
import io.scalac.core.model._
import io.scalac.core.util.stream.subStreamNameFromActorRef
import io.scalac.extension.event.ActorInterpreterStats

import java.lang.invoke.MethodType._
object ActorGraphInterpreterOps extends Lookup {

  private lazy val shells = {
    val actorInterpreter = Class.forName("akka.stream.impl.fusing.ActorGraphInterpreter")
    lookup.findVirtual(actorInterpreter, "activeInterpreters", methodType(classOf[Set[GraphInterpreterShellMirror]]))
  }

  def addCollectionReceive(
    receive: Actor.Receive,
    thiz: Actor
  ): Actor.Receive =
    receive.orElse { case PushMetrics(replyTo) =>
      val subStreamName = subStreamNameFromActorRef(thiz.context.self)

      val currentShells = shells
        .invoke(thiz)
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

      val self = thiz.context.self

      replyTo ! ActorInterpreterStats(self, subStreamName, stats)
    }

}
