package io.scalac.mesmer.extension.metric

import java.util

import org.slf4j.LoggerFactory

import scala.collection.mutable.{ Map => MutableMap }
import scala.jdk.CollectionConverters._

import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.extension.config.CachingConfig

case class CachingMonitor[L <: LabelSerializable, B <: Bound](
  bindable: Bindable[L, B],
  config: CachingConfig = CachingConfig.empty
) extends Bindable[L, B] {

  private val logger = LoggerFactory.getLogger(CachingMonitor.getClass)

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
    cachedMonitors.getOrElseUpdate(
      labels, {
        logger.debug("Creating new monitor for lables {}", labels)
        bindable(labels)
      }
    )

}
