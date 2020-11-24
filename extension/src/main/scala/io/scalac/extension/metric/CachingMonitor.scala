package io.scalac.extension.metric
import org.slf4j.LoggerFactory

import scala.collection.mutable.{ Map => MutableMap }

class CachingMonitor[L, B](val monitor: Bindable.Aux[L, B]) extends Bindable[L] {
  private[this] val logger = LoggerFactory.getLogger(this.getClass)

  private[this] val cachedMonitors: MutableMap[L, monitor.Bound] = MutableMap.empty

  override type Bound = monitor.Bound

  override final def bind(node: L): Bound =
    cachedMonitors.getOrElse(node, updateMonitors(node, monitor.bind(node)))

  protected def updateMonitors(labels: L, newMonitor: => Bound): Bound =
    cachedMonitors.getOrElseUpdate(labels, {
      logger.debug("Creating new monitor for lables {}", labels)
      newMonitor
    })
}

object CachingMonitor {
  implicit class CachingMonitorOps[L, B](val monitor: Bindable.Aux[L, B]) {
    def caching[B]: Bindable.Aux[L, monitor.Bound] = new CachingMonitor[L, monitor.Bound](monitor)
  }
}
