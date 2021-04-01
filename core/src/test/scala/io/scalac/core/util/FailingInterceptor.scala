package io.scalac.core.util

import akka.actor.typed._
import io.scalac.core.util.FailingInterceptor.sendFailSignal

import scala.reflect.ClassTag
import scala.util.control.NoStackTrace

import io.scalac.extension.util.FailingInterceptor.sendFailSignal

private class FailingInterceptor[A: ClassTag] private (val probe: Option[ActorRef[A]])
    extends BehaviorInterceptor[A, A] {

  import FailingInterceptor._

  override def aroundReceive(
    ctx: TypedActorContext[A],
    msg: A,
    target: BehaviorInterceptor.ReceiveTarget[A]
  ): Behavior[A] = {
    probe.foreach(_ ! msg)
    target.apply(ctx, msg)
  }

  override def aroundSignal(
    ctx: TypedActorContext[A],
    signal: Signal,
    target: BehaviorInterceptor.SignalTarget[A]
  ): Behavior[A] =
    signal match {
      case Fail   => throw FailException
      case signal => super.aroundSignal(ctx, signal, target)
    }
}

trait ActorFailing {
  implicit class FailOps(actor: ActorRef[_]) {
    def fail(): Unit = sendFailSignal(actor)
  }
}

object FailingInterceptor extends ActorFailing {

  private case object Fail extends Signal

  private case object FailException extends RuntimeException("Planned actor failure") with NoStackTrace

  def sendFailSignal(actorRef: ActorRef[_]): Unit = {
    val internalActor = actorRef.unsafeUpcast[Any]
    internalActor ! Fail
  }

  def apply[T: ClassTag](probe: ActorRef[T]): BehaviorInterceptor[T, T] = new FailingInterceptor[T](Some(probe))
  def apply[T: ClassTag]: BehaviorInterceptor[T, T]                     = new FailingInterceptor[T](None)
}
