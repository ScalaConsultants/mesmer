package io.scalac.extension.resource
import scala.collection.concurrent.{ Map => CMap }

trait MutableStorage[K, V] {
  protected def buffer: CMap[K, V]
}
