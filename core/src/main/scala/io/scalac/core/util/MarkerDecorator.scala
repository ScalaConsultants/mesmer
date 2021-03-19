package io.scalac.core.util

import java.util.concurrent.atomic.AtomicBoolean

final class MarkerDecorator {
  private val marker           = new AtomicBoolean(false)
  def mark(): Unit             = marker.set(true)
  def checkAndReset(): Boolean = marker.getAndSet(false)
}
