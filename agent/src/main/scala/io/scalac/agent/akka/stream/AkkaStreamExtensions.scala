package io.scalac.agent.akka.stream

import akka.AkkaMirrorTypes.GraphInterpreterShellMirror
import io.scalac.core.PushMetrics

import java.lang.invoke.MethodHandles._
import java.lang.invoke.MethodType._
object AkkaStreamExtensions {

  private lazy val graphInterpreterClass = Class.forName("akka.stream.impl.fusing.GraphInterpreter")

  private lazy val graphInterpreterPushCounter = {
    val field = graphInterpreterClass.getDeclaredField("pushCounter")
    field.setAccessible(true)
    lookup().unreflectGetter(field)
  }

  private lazy val graphInterpreterPullCounter = {
    val field = graphInterpreterClass.getDeclaredField("pullCounter")
    field.setAccessible(true)
    lookup().unreflectGetter(field)
  }

  private lazy val shells = {
    val actorInterpreter = Class.forName("akka.stream.impl.fusing.ActorGraphInterpreter")
    lookup().findVirtual(actorInterpreter, "activeInterpreters", methodType(classOf[Set[GraphInterpreterShellMirror]]))
  }

  def addCollectionReceive(
    receive: PartialFunction[Any, Unit],
    self: AnyRef
  ): PartialFunction[Any, Unit] =
    receive.orElse {
      case PushMetrics => {

        shells.invoke(self).asInstanceOf[Set[GraphInterpreterShellMirror]].map(_.interpreter).foreach { inter =>
          val push = graphInterpreterPushCounter.invoke(inter).asInstanceOf[Int]
          val pull = graphInterpreterPullCounter.invoke(inter).asInstanceOf[Int]
          println(s"${inter}: push ${push}, pull ${pull}")
        }
      }
    }

}
