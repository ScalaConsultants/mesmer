package io.scalac.core.actor

import akka.actor.ActorRef
import io.scalac.core.model.{ ActorKey, _ }

import io.scalac.core.util.ActorPathOps

trait ActorMetricStorage {
  def save(actorRef: ActorRef, metrics: ActorMetrics): ActorMetricStorage
  def remove(key: ActorKey): ActorMetricStorage
  def foreach(f: ((ActorKey, ActorMetrics)) => Unit): Unit
  def snapshot: Seq[(ActorKey, ActorMetrics)]
  def has(key: ActorKey): Boolean
  def clear(): ActorMetricStorage
  private[scalac] def actorToKey(actorRef: ActorRef): ActorKey = ActorPathOps.getPathString(actorRef)
}
