package io.scalac.agent.akka.stream

import akka.AkkaMirrorTypes._
import akka.actor.Actor
import akka.actor.typed.scaladsl.adapter._
import akka.stream.GraphLogicOps._
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.invoke.Lookup
import io.scalac.core.model.ConnectionStats
import io.scalac.extension.event.{ ActorInterpreterStats, EventBus }

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
    self: Actor
  ): PartialFunction[Any, Unit] =
    receive.orElse {
      case PushMetrics => {
        val stats = shells
          .invoke(self)
          .asInstanceOf[Set[GraphInterpreterShellMirror]]
          .flatMap(_.connections)
          .map { connection =>
            val (push, pull) = ConnectionOps.getAndResetCounterValues(connection)
            val in           = connection.inOwner.stageName
            val out          = connection.outOwner.stageName

            ConnectionStats(in, out, push, pull)
          }
        shells
          .invoke(self)
          .asInstanceOf[Set[GraphInterpreterShellMirror]]
          .flatMap(_.logics)
          .map(logic => {
            logic.stageName
          })

        EventBus(self.context.system.toTyped).publishEvent(ActorInterpreterStats(self.context.self, stats, None))
      }
    }

}
