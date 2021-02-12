package io.scalac.extension.actor

import scala.collection.mutable

import akka.actor.ActorRef

import io.scalac.extension.resource.MutableStorage

class MutableActorMetricsStorage private[actor] (override val buffer: mutable.Map[ActorKey, ActorMetrics])
    extends MutableStorage[ActorKey, ActorMetrics]
    with ActorMetricStorage {

  def foreach(f: ((ActorKey, ActorMetrics)) => Unit): Unit = buffer.foreach(f)

  def save(actorRef: ActorRef, metrics: ActorMetrics): ActorMetricStorage = {
    buffer(actorToKey(actorRef)) = metrics
    this
  }
}

object MutableActorMetricsStorage {
  def empty: MutableActorMetricsStorage = new MutableActorMetricsStorage(mutable.Map.empty)
}
