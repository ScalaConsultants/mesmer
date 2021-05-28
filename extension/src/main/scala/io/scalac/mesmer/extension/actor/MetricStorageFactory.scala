package io.scalac.mesmer.extension.actor

trait MetricStorageFactory[K] {
  type Storage <: MetricStorage

  protected trait MetricStorage {

    /**
     * @param key
     * @param metrics
     * @param persistent if metrics should be returned from iterable even after compute
     * @return
     */
    def save(key: K, metrics: ActorMetrics, persistent: Boolean): Storage
//    def remove(key: ActorKey): Storage
//    def foreach(f: ((ActorKey, ActorMetrics)) => Unit): Unit
    /**
     * All persistent metrics
     * @return
     */
    def iterable: Iterable[(K, ActorMetrics)]
//    def has(key: ActorKey): Boolean
    def compute(key: K): Storage
//    def clear(): Storage
  }

//  protected def actorToKey(actorRef: ActorRef): K = ActorPathOps.getPathString(actorRef)

  def createStorage: Storage
  def mergeStorage(first: Storage, second: Storage): Storage
}
