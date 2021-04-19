package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.BehaviorInterceptor
import akka.actor.typed.TypedActorContext
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration._
import scala.reflect.ClassTag

import io.scalac.extension.resource.SelfCleaning

class WithSelfCleaningState
object WithSelfCleaningState {

  private[WithSelfCleaningState] final case class CleanResource(resourceTag: ClassTag[_])

  private class WithSelfCleaningStateInterceptor[R <: SelfCleaning, C] private[WithSelfCleaningState] (
    private val resource: R
  )(implicit val _tag: ClassTag[R])
      extends BehaviorInterceptor[Any, C] {
    def aroundReceive(
      ctx: TypedActorContext[Any],
      msg: Any,
      target: BehaviorInterceptor.ReceiveTarget[C]
    ): Behavior[C] = msg match {
      case CleanResource(tag) if tag == _tag =>
        ctx.asScala.log.debug("Cleaning up resource {}", tag)
        resource.clean()
        Behaviors.same
      case internal: C @unchecked => target.apply(ctx, internal)
    }
  }

  // Using builder make type inference happy
  class Builder[R <: SelfCleaning] private[WithSelfCleaningState] (
    private val state: R
  )(implicit val tag: ClassTag[R]) {
    def every[C](duration: FiniteDuration)(internal: R => Behavior[C]): Behavior[C] =
      Behaviors
        .withTimers[Any] { timer =>
          timer.startTimerWithFixedDelay(CleanResource(tag), duration)
          Behaviors.intercept(() => new WithSelfCleaningStateInterceptor[R, C](state))(internal(state))
        }
        .narrow[C]
  }

  def clean[R <: SelfCleaning: ClassTag](resource: R): Builder[R] = new Builder[R](resource)
}
