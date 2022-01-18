package io.scalac.mesmer.extension.actor

trait MetricStorageFactory[K] {
  type Storage <: MetricStorage

  protected trait MetricStorage {

    /**
     * @param key
     * @param metrics
     * @param persistent
     *   if metrics should be returned from iterable
     * @return
     */
    def save(key: K, metrics: ActorMetrics, persistent: Boolean): Storage

    /**
     * All persistent metrics
     * @return
     */
    def iterable: Iterable[(K, ActorMetrics)]
    def compute(key: K): Storage
  }

  def createStorage: Storage
  def mergeStorage(first: Storage, second: Storage): Storage
}
