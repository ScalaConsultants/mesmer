package io.scalac.core.akka

import akka.actor.ActorRef

package object model {

  /**
   * Command signalling that actor should send accumulated metrics in reply
   */
  private[scalac] case object PushMetrics
}
