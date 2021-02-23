package io.scalac.extension.actor

import akka.actor.ActorRef
import io.scalac.extension.model.ActorKey

trait ActorMetricStorage {
  def save(actorRef: ActorRef, metrics: ActorMetrics): ActorMetricStorage
  def foreach(f: ((ActorKey, ActorMetrics)) => Unit): Unit
  def has(key: ActorKey): Boolean
  private[extension] def actorToKey(actorRef: ActorRef): ActorKey = actorRef.path.toStringWithoutAddress
}
