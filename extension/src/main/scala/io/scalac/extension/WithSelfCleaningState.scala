package io.scalac.extension

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ Behavior, BehaviorInterceptor, TypedActorContext }
import io.scalac.extension.WithSelfCleaningState.Builder.{ BuilderState, Empty, WithDuration }
import io.scalac.extension.persistence.SelfCleaning

import scala.concurrent.duration._
import scala.language.postfixOps

class WithSelfCleaningState
object WithSelfCleaningState {

  final case object Clean

  private class WithSelfCleaningStateInterceptor[C] private[WithSelfCleaningState] (private val resource: SelfCleaning)
      extends BehaviorInterceptor[Any, C] {
    override def aroundReceive(
      ctx: TypedActorContext[Any],
      msg: Any,
      target: BehaviorInterceptor.ReceiveTarget[C]
    ): Behavior[C] = msg match {
      case Clean => {
        ctx.asScala.log.info("Cleaning up resource")
        resource.clean()
        Behaviors.same
      }
      case internal: C @unchecked => target.apply(ctx, internal)
    }
  }

  // Using builder make type inference happy
  class Builder[T <: SelfCleaning, BS <: BuilderState] private[WithSelfCleaningState] (
    private val state: T,
    private val _every: Option[FiniteDuration] = None
  ) {
    def every(duration: FiniteDuration): Builder[T, WithDuration] = new Builder[T, WithDuration](state, Some(duration))

    def `for`[C](internal: T => Behavior[C])(implicit ev: BS =:= WithDuration): Behavior[C] =
      Behaviors
        .withTimers[Any] { timer =>
          timer.startTimerWithFixedDelay(Clean, _every.get)
          Behaviors.intercept(() => new WithSelfCleaningStateInterceptor[C](state))(internal(state))
        }
        .narrow[C]
  }

  object Builder {
    sealed trait BuilderState
    trait Empty        extends BuilderState
    trait WithDuration extends BuilderState
  }

  def clean[T <: SelfCleaning](resource: T): Builder[T, Empty] = new Builder[T, Empty](resource)
}
