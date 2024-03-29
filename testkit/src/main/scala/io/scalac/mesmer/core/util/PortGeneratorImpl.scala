package io.scalac.mesmer.core.util

import scala.collection.mutable.ListBuffer
import scala.util.Random

private[scalac] object PortGeneratorImpl extends PortGenerator {

  private val _takenPorts: ListBuffer[Int] = ListBuffer.empty

  type Port = PortInternal
  case class PortInternal(port: Int) extends PortLike

  private def generateRandomPort(): Int =
    1024 + Random.nextInt(20000)

  def generatePort(): Port = _takenPorts.synchronized {
    val first = LazyList
      .continually(
        generateRandomPort()
      )
      .filter(elem => !_takenPorts.contains(elem))
      .head

    _takenPorts += first
    PortInternal(first)
  }

  def releasePort(port: Port): Unit = _takenPorts.synchronized(_takenPorts -= port.port)
}
