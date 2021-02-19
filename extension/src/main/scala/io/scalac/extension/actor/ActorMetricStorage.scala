package io.scalac.extension.actor

import akka.actor.ActorRef

trait ActorMetricStorage {
  def save(actorRef: ActorRef, metrics: ActorMetrics): ActorMetricStorage
  def remove(key: ActorKey): ActorMetricStorage
  def map[T](f: ((ActorKey, ActorMetrics)) => T): Iterable[T]
  def actorToKey(actorRef: ActorRef): String = actorRef.path.toStringWithoutAddress
}
