package io.scalac.agent.akka.stream

import akka.AkkaMirrorTypes._
import akka.actor.Actor
import akka.actor.typed.scaladsl.adapter._
import akka.stream.GraphLogicOps._
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.invoke.Lookup
import io.scalac.core.model._
import io.scalac.core.util.stream.streamNameFromActorRef
import io.scalac.extension.event.{ActorInterpreterStats, EventBus}

import java.lang.invoke.MethodType._
object AkkaStreamExtensions extends Lookup {

  private lazy val graphInterpreterClass = Class.forName("akka.stream.impl.fusing.GraphInterpreter")

  private lazy val graphInterpreterPushCounter = {
    val field = graphInterpreterClass.getDeclaredField("pushCounter")
    field.setAccessible(true)
    lookup.unreflectGetter(field)
  }

  private lazy val graphInterpreterPullCounter = {
    val field = graphInterpreterClass.getDeclaredField("pullCounter")
    field.setAccessible(true)
    lookup.unreflectGetter(field)
  }

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

        val streamName = streamNameFromActorRef(thiz.context.self)

        val currentShells = shells
          .invoke(thiz)
          .asInstanceOf[Set[GraphInterpreterShellMirror]]

        val stats = currentShells.map { shell =>
          val connections =shell.connections.map { connection =>
            val (push, pull) = ConnectionOps.getAndResetCounterValues(connection)
            val in           = connection.inOwner.streamUniqueStageName
            val out          = connection.outOwner.streamUniqueStageName

            ConnectionStats(in, out, push, pull)
          }
          val stageInfo = shell.logics.map(logic => StageInfo(logic.streamUniqueStageName, streamName))
          (stageInfo, connections)
        }

        val self = thiz.context.self

        replyTo ! ActorInterpreterStats(self, streamName, stats)
      }
    }

}
