package io.scalac.core.model

import io.scalac.core.util.Timestamp

trait Entry[T] {
  def value: T
  def timestamp: Timestamp
}
