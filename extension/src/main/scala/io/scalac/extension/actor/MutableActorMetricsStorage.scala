package io.scalac.extension.actor

import akka.actor.ActorRef

import scala.collection.mutable

import io.scalac.core.model.ActorKey
import io.scalac.extension.resource.MutableStorage

class MutableActorMetricsStorage private[actor] (val buffer: mutable.Map[ActorKey, ActorMetrics])
    extends MutableStorage[ActorKey, ActorMetrics]
    with ActorMetricStorage {

  def has(key: ActorKey): Boolean = buffer.contains(key)

  def foreach(f: ((ActorKey, ActorMetrics)) => Unit): Unit = buffer.foreach(f)

  def save(actorRef: ActorRef, metrics: ActorMetrics): ActorMetricStorage = {
    buffer(actorToKey(actorRef)) = metrics
    this
  }

  def remove(key: ActorKey): ActorMetricStorage = {
    buffer.remove(key)
    this
  }

  def clear(): ActorMetricStorage = {
    buffer.clear()
    this
  }

  def snapshot: Seq[(ActorKey, ActorMetrics)] = buffer.toSeq
}

object MutableActorMetricsStorage {
  def empty: MutableActorMetricsStorage = new MutableActorMetricsStorage(mutable.Map.empty)
}
