package io.scalac.extension.resource
import scala.collection.mutable.{ Map => MMap }

trait MutableStorage[K, V] {
  protected def buffer: MMap[K, V]
}
