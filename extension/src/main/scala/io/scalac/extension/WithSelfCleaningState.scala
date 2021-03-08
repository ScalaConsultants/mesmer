package io.scalac.extension

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ Behavior, BehaviorInterceptor, TypedActorContext }
import io.scalac.extension.resource.SelfCleaning

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag

class WithSelfCleaningState
object WithSelfCleaningState {

  private[WithSelfCleaningState] final case class CleanResource(resourceTag: ClassTag[_])

  private class WithSelfCleaningStateInterceptor[R <: SelfCleaning, C] private[WithSelfCleaningState] (
    private val resource: R
  )(implicit val _tag: ClassTag[R])
      extends BehaviorInterceptor[Any, C] {
    override def aroundReceive(
      ctx: TypedActorContext[Any],
      msg: Any,
      target: BehaviorInterceptor.ReceiveTarget[C]
    ): Behavior[C] = msg match {
      case CleanResource(tag) if tag == _tag => {
        ctx.asScala.log.debug("Cleaning up resource {}", tag)
        resource.clean()
        Behaviors.same
      }
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
