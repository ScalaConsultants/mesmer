package io.scalac.extension.metric
import org.slf4j.LoggerFactory

import scala.collection.mutable.{ Map => MutableMap }

class CachingMonitor[L, B, T <: Bindable.Aux[L, B]](val monitor: T) extends Bindable[L] {
  private[this] val logger = LoggerFactory.getLogger(this.getClass)

  private[this] val cachedMonitors: MutableMap[L, monitor.Bound] = MutableMap.empty

  override type Bound = B

  override final def bind(node: L): Bound =
    cachedMonitors.getOrElse(node, updateMonitors(node, monitor.bind(node)))

  protected def updateMonitors(labels: L, newMonitor: => Bound): Bound =
    cachedMonitors.getOrElseUpdate(labels, {
      logger.debug("Creating new monitor for lables {}", labels)
      newMonitor
    })
}

class Dependent extends Bindable[Unit] {

  override type Bound = BoundMonitor

  override def bind(lables: Unit): Bound = new BoundMonitor {}

  trait BoundMonitor {
    def testBoundMonitor(): Unit = ()
  }
}

object CachingMonitor {
  /*
   * Due to limitations of type interference this definition differ from CachingMonitor
   */
  implicit class CachingMonitorOps[L, B](monitor: Bindable.Aux[L, B]) {
    def caching: Bindable.Aux[L, B] = new CachingMonitor[L, B, Bindable.Aux[L, B]](monitor)
  }
}
