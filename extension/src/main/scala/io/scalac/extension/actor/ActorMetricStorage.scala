package io.scalac.extension.actor

import akka.actor.ActorRef

trait ActorMetricStorage {
  def save(actorRef: ActorRef, metrics: ActorMetrics): ActorMetricStorage
  def remove(key: ActorKey): ActorMetricStorage
  def foreach(f: ((ActorKey, ActorMetrics)) => Unit): Unit
  def clear(): ActorMetricStorage
  def actorToKey(actorRef: ActorRef): String = actorRef.path.toStringWithoutAddress
}
