package io.scalac.mesmer.extension.actor

import akka.actor.ActorRef

import io.scalac.mesmer.core.model.ActorKey
import io.scalac.mesmer.core.util.ActorPathOps

trait ActorMetricStorage {
  def save(actorRef: ActorRef, metrics: ActorMetrics): ActorMetricStorage
  def remove(key: ActorKey): ActorMetricStorage
  def foreach(f: ((ActorKey, ActorMetrics)) => Unit): Unit
  def snapshot: Seq[(ActorKey, ActorMetrics)]
  def has(key: ActorKey): Boolean
  def clear(): ActorMetricStorage
  private[scalac] def actorToKey(actorRef: ActorRef): ActorKey = ActorPathOps.getPathString(actorRef)
}
