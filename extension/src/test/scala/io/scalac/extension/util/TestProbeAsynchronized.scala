package io.scalac.extension.util

import scala.concurrent.duration._

import akka.actor.typed.ActorSystem

import io.scalac.extension.metric.Asynchronized.CallbackListAsynchronized

trait TestProbeAsynchronized extends CallbackListAsynchronized {

  def pingOffset: FiniteDuration
  implicit def actorSystem: ActorSystem[_]

  actorSystem.scheduler.scheduleWithFixedDelay(pingOffset / 2, pingOffset)(() => callCallbacks())(
    actorSystem.executionContext
  )

}
