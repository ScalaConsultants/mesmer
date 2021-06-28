package io.scalac.mesmer.extension.actor

import scala.collection.mutable

import io.scalac.mesmer.extension.resource.MutableStorage

final class MutableActorMetricStorageFactory[K] extends MetricStorageFactory[K] {
  type Storage = MutableActorMetricsStorage

  final class MutableActorMetricsStorage private[actor](
    protected val buffer: mutable.Map[K, ActorMetrics],
    protected val persistentBuffer: mutable.Map[K, ActorMetrics]
  ) extends MutableStorage[K, ActorMetrics]
      with MetricStorage {

    /**
     * @param actorRef
     * @param metrics
     * @param persistent if metrics should be returned from iterable
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
      val result = buffer.values.fold(ActorMetrics.empty)(_.sum(_))
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
