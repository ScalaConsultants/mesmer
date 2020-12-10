package io.scalac.extension.metric
import org.slf4j.LoggerFactory

import scala.collection.mutable.{Map => MutableMap}

class CachingMonitor[L <: AnyRef, T <: Bindable[L]](val monitor: T) extends Bindable[L] {
  private[this] val logger = LoggerFactory.getLogger(this.getClass)

  private[this] val cachedMonitors: MutableMap[L, Bound] = MutableMap.empty

  override type Bound = T#Bound

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
   * Due to limitations of type interference type parameters has to be manually assigned
   * see https://github.com/scala/bug/issues/5298
   */
  def caching[L <: AnyRef, V <: Bindable[L]](monitor: V): Bindable.Aux[L, V#Bound] = new CachingMonitor[L, V](monitor)

}
