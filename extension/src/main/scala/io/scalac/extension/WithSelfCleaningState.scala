package io.scalac.extension

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ Behavior, BehaviorInterceptor, TypedActorContext }
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
  class Builder[T <: SelfCleaning] private[WithSelfCleaningState] (
    private val state: T
  ) {
    def every[C](duration: FiniteDuration)(internal: T => Behavior[C]): Behavior[C] =
      Behaviors
        .withTimers[Any] { timer =>
          timer.startTimerWithFixedDelay(Clean, duration)
          Behaviors.intercept(() => new WithSelfCleaningStateInterceptor[C](state))(internal(state))
        }
        .narrow[C]
  }

  def clean[T <: SelfCleaning](resource: T): Builder[T] = new Builder[T](resource)
}
