package io.scalac.core.util

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration._

final class TimerDecorator {
  private val timestamp          = new AtomicReference[Timestamp]()
  def start(): Unit              = timestamp.set(Timestamp.create())
  def interval(): FiniteDuration = timestamp.get().interval().milliseconds
}
