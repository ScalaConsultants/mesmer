package io.scalac.extension.actor

import akka.actor.ActorRef
import io.scalac.core.model.{ ActorKey, _ }

trait ActorMetricStorage {
  def save(actorRef: ActorRef, metrics: ActorMetrics): ActorMetricStorage
  def remove(key: ActorKey): ActorMetricStorage
  def foreach(f: ((ActorKey, ActorMetrics)) => Unit): Unit
  def has(key: ActorKey): Boolean
  def clear(): ActorMetricStorage
  private[extension] def actorToKey(actorRef: ActorRef): ActorKey = actorRef.path.toActorPath
}
