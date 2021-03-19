package io.scalac.core.util

import java.util.concurrent.atomic.AtomicLong

final class CounterDecorator {
  private val counter = new AtomicLong(0)
  def inc(): Unit     = counter.getAndIncrement()
  def take(): Long    = counter.getAndSet(0)
  def get(): Long     = counter.get()
  def reset(): Unit   = counter.set(0)
}
