package io.scalac.extension.metric

import java.util

import scala.collection.mutable.{ Map => MutableMap }
import scala.jdk.CollectionConverters._

import org.slf4j.LoggerFactory

import io.scalac.extension.config.CachingConfig

case class CachingMonitor[L, B <: Bound](bindable: Bindable[L, B], config: CachingConfig = CachingConfig.empty)
    extends Bindable[L, B] {

  import CachingMonitor.logger

  private[extension] val cachedMonitors: MutableMap[L, B] = {
    val cacheAccessOrder = true // This constant must be hardcoded to ensure LRU policy
    val loadFactor       = 0.75f
    new util.LinkedHashMap[L, B](config.maxEntries, loadFactor, cacheAccessOrder) {
      override def removeEldestEntry(eldest: util.Map.Entry[L, B]): Boolean =
        if (size() > config.maxEntries) {
          eldest.getValue.unbind()
          true
        } else false
    }.asScala
  }

  final def bind(labels: L): B =
    cachedMonitors.getOrElse(labels, updateMonitors(labels))

  final private def updateMonitors(labels: L): B =
    cachedMonitors.getOrElseUpdate(labels, {
      logger.debug("Creating new monitor for lables {}", labels)
      bindable(labels)
    })

}

object CachingMonitor {
  private val logger = LoggerFactory.getLogger(CachingMonitor.getClass)
}
