package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.typed.{ Cluster, SelfUp, Subscribe }

import scala.concurrent.duration.FiniteDuration

object OnClusterStartUp {

  private case class Initialized(currentClusterState: CurrentClusterState)
  private case object Timeout
  private val timeoutTimerKey = "WithTimeoutKey"

  def apply[T](inner: Behavior[T], timeout: Option[FiniteDuration]): Behavior[T] =
    Behaviors
      .setup[Any] { ctx =>
        def init: Behavior[Any] = Behaviors.withTimers { timer =>
          timeout.foreach(timeoutDuration => timer.startSingleTimer(timeoutTimerKey, Timeout, timeoutDuration))
          Behaviors.withStash(1024) { stash =>
            Behaviors.receiveMessage {
              case Timeout =>
                ctx.log.warn(s"Initialization timed out")
                Behaviors.stopped
              case Initialized(_) =>
                ctx.log.info("Cluster initialized")
                timer.cancel(timeoutTimerKey)
                stash.unstashAll(inner.asInstanceOf[Behavior[Any]])
              case message: T @unchecked =>
                stash.stash(message)
                Behaviors.same
              case _ => Behaviors.unhandled
            }
          }
        }

        val adapter = ctx.messageAdapter[SelfUp](selfUp => Initialized(selfUp.currentClusterState))
        Cluster(ctx.system).subscriptions ! Subscribe(adapter, classOf[SelfUp])
        init
      }
      .narrow[T]

}
