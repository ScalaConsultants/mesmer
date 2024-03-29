package io.scalac.mesmer.core.util

trait PortLike {
  def port: Int
}

trait PortGenerator {
  type Port <: PortLike
  def generatePort(): Port
  def releasePort(port: Port): Unit
}
