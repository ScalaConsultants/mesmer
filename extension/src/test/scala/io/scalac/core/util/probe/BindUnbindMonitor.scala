package io.scalac.core.util.probe

import java.util.concurrent.atomic.AtomicInteger

import io.scalac.extension.metric.Bound

trait BindUnbindMonitor {

  // monotonic counters
  private val _binds   = new AtomicInteger(0)
  private val _unbinds = new AtomicInteger(0)

  protected def onBind(): Unit = _binds.incrementAndGet()

  def binds: Int   = _binds.get()
  def unbinds: Int = _unbinds.get()

  /**
   * Mixed-in trait for BoundMonitors returned from bind method
   */
  trait UnbindMonitor extends Bound {
    override abstract def unbind(): Unit = {
      _unbinds.incrementAndGet()
      super.unbind()
    }
  }
}
