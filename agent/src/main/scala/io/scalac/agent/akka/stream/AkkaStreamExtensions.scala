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
object AkkaStreamExtensions extends Lookup {

  private lazy val shells = {
    val actorInterpreter = Class.forName("akka.stream.impl.fusing.ActorGraphInterpreter")
    lookup.findVirtual(actorInterpreter, "activeInterpreters", methodType(classOf[Set[GraphInterpreterShellMirror]]))
  }

  def addCollectionReceive(
    receive: PartialFunction[Any, Unit],
    thiz: Actor
  ): PartialFunction[Any, Unit] =
    receive.orElse {
      case PushMetrics(replyTo) => {

        val subStreamName = subStreamNameFromActorRef(thiz.context.self)

        val currentShells = shells
          .invoke(thiz)
          .asInstanceOf[Set[GraphInterpreterShellMirror]]
        var terminalFound = false

        val stats = currentShells.map { shell =>
          val connections = shell.connections.flatMap { connection =>
            val (push, pull) = ConnectionOps.getAndResetCounterValues(connection)

            for {
              in  <- connection.inOwner.streamUniqueStageName
              out <- connection.outOwner.streamUniqueStageName
            } yield ConnectionStats(in, out, push, pull)
          }

          val stageInfo = shell.logics.flatMap { logic =>
            val isTerminal =
              if (terminalFound) false
              else {
                val terminal = logic.isTerminal
                if (terminal) {
                  terminalFound = true
                }
                terminal
              }
            logic.streamUniqueStageName.map(StageInfo(_, subStreamName, isTerminal))
          }
          stageInfo -> connections

        }

        val self = thiz.context.self

        replyTo ! ActorInterpreterStats(self, subStreamName, stats)
      }
    }

}
