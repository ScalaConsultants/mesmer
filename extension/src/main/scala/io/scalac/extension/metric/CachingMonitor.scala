package io.scalac.extension.metric
import org.slf4j.LoggerFactory

import java.util.{ LinkedHashMap, Map }
import scala.collection.mutable.{ Map => MutableMap }
import scala.jdk.CollectionConverters._

class CachingMonitor[L <: AnyRef, B <: Unbind, T <: Bindable.Aux[L, B]](val monitor: T, maxEntries: Int = 100)
    extends Bindable[L] {
  private[this] val logger = LoggerFactory.getLogger(this.getClass)

  private[this] val cachedMonitors: MutableMap[L, Bound] = new LinkedHashMap[L, B](maxEntries, 1.0f, true) {
    override def removeEldestEntry(eldest: Map.Entry[L, Bound]): Boolean =
      if (size() > maxEntries) {
        eldest.getValue.unbind()
        true
      } else false
  }.asScala

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
  /*
   * Due to limitations of type interference type parameters has to be manually assigned
   * see https://github.com/scala/bug/issues/5298
   */
  def caching[L <: AnyRef, V <: Bindable[L]](monitor: V): Bindable.Aux[L, monitor.Bound] =
    new CachingMonitor[L, monitor.Bound, monitor.type](monitor)

}
