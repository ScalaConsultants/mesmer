package io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.Member
import akka.cluster.typed.Cluster
import akka.cluster.typed.SelfUp
import akka.cluster.typed.Subscribe
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

object OnClusterStartup {

  private val log = LoggerFactory.getLogger(OnClusterStartup.getClass)

  private case class Initialized(currentClusterState: CurrentClusterState)

  private case object Timeout

  private val timeoutTimerKey = "WithTimeoutKey"

  def upTo[T](timeout: FiniteDuration)(inner: Member => Behavior[T]): Behavior[T] =
    internalApply(inner, Some(timeout))

  def apply[T](inner: Member => Behavior[T]): Behavior[T] =
    internalApply(inner, None)

  def internalApply[T](inner: Member => Behavior[T], timeout: Option[FiniteDuration]): Behavior[T] =
    Behaviors
      .setup[Any] { ctx =>
        def init: Behavior[Any] = Behaviors.withTimers { timer =>
          timeout.foreach(timeoutDuration => timer.startSingleTimer(timeoutTimerKey, Timeout, timeoutDuration))
          Behaviors.withStash(1024) { stash =>
            Behaviors.receiveMessage {
              case Timeout =>
                log.warn("Initialization timed out")
                Behaviors.stopped
              case Initialized(_) =>
                log.info("Cluster initialized")
                timer.cancel(timeoutTimerKey)
                val selfMember = Cluster(ctx.system).selfMember
                stash.unstashAll(inner(selfMember).asInstanceOf[Behavior[Any]])
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
