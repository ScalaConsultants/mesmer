package io.scalac.mesmer.extension.actor

import io.scalac.mesmer.extension.resource.MutableStorage

import scala.collection.mutable

final class MutableActorMetricStorageFactory[K] extends MetricStorageFactory[K] {
  type Storage = MutableActorMetricsStorage

  final class MutableActorMetricsStorage private[actor] (
    protected val buffer: mutable.Map[K, ActorMetrics],
    protected val persistentBuffer: mutable.Map[K, ActorMetrics]
  ) extends MutableStorage[K, ActorMetrics]
      with MetricStorage {

//    def has(key: ActorKey): Boolean = buffer.contains(key)

//    def foreach(f: ((ActorKey, ActorMetrics)) => Unit): Unit = buffer.foreach(f)

//    def remove(key: ActorKey): this.type = {
//      buffer.remove(key)
//      this
//    }
//
//    def clear(): this.type = {
//      buffer.clear()
//      this
//    }
//
//    def snapshot: Seq[(ActorKey, ActorMetrics)] = buffer.toSeq

    /**
     * @param actorRef
     * @param metrics
     * @param persistent if metrics should be returned from iterable even after compute
     * @return
     */
    def save(key: K, metrics: ActorMetrics, persistent: Boolean): this.type = {
      buffer(key) = metrics
      if (persistent) {
        persistentBuffer(key) = metrics
      }
      this
    }

    /**
     * All persistent metrics
     *
     * @return
     */
    def iterable: Iterable[(K, ActorMetrics)] = persistentBuffer.toVector

    def compute(key: K): this.type = {
      val result = buffer.values.fold(ActorMetrics.empty)(_.combine(_))
      buffer.clear()
      save(key, result, true)
      this
    }

    def merge(other: Storage): Storage =
      new MutableActorMetricsStorage(this.buffer ++ other.buffer, this.persistentBuffer ++ other.persistentBuffer)

    /**
     * Exists for testing purpose
     */
    private[actor] def buffers: (mutable.Map[K, ActorMetrics], mutable.Map[K, ActorMetrics]) =
      (buffer, persistentBuffer)
  }

  def createStorage: Storage = new MutableActorMetricsStorage(mutable.Map.empty, mutable.Map.empty)

  def mergeStorage(first: Storage, second: Storage): Storage = first.merge(second)
}
