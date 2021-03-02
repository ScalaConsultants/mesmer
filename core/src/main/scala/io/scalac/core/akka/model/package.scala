package io.scalac.core.akka

package object model {

  /**
   * Command signalling that actor should push accumulated metrics to extension
   */
  private[scalac] case object PushMetrics

}
