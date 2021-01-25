package io.scalac.extension.metric

import java.util

import scala.collection.mutable.{ Map => MutableMap }
import scala.jdk.CollectionConverters._

import org.slf4j.LoggerFactory

import io.scalac.extension.config.CachingConfig

class CachingMonitor[L <: AnyRef, B <: Unbind, T <: Bindable.Aux[L, B]](
  val monitor: T,
  config: CachingConfig = CachingConfig.empty
) extends Bindable[L] {
  import CachingMonitor._

  private[metric] val cachedMonitors: MutableMap[L, Bound] = {
    val cacheAccessOrder = true // This constant must be hardcoded to ensure LRU policy
    new util.LinkedHashMap[L, B](config.maxEntries, config.loadFactor, cacheAccessOrder) {
      override def removeEldestEntry(eldest: util.Map.Entry[L, Bound]): Boolean =
        if (size() > config.maxEntries) {
          eldest.getValue.unbind()
          true
        } else false
    }.asScala
  }

  override type Bound = B

  override final def bind(node: L): Bound =
    cachedMonitors.getOrElse(node, updateMonitors(node, monitor.bind(node)))

  protected def updateMonitors(labels: L, newMonitor: => Bound): Bound =
    cachedMonitors.getOrElseUpdate(labels, {
      logger.debug("Creating new monitor for lables {}", labels)
      newMonitor
    })
}

object CachingMonitor {
  private[CachingMonitor] val logger = LoggerFactory.getLogger(CachingMonitor.getClass)

  /*
   * Due to limitations of type interference type parameters has to be manually assigned
   * see https://github.com/scala/bug/issues/5298
   */
  def caching[L <: AnyRef, V <: Bindable[L]](
    monitor: V,
    config: CachingConfig = CachingConfig.empty
  ): Bindable.Aux[L, monitor.Bound] =
    new CachingMonitor[L, monitor.Bound, monitor.type](monitor, config)

}
