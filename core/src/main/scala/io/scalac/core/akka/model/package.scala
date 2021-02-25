package io.scalac.core.akka

import scala.concurrent.duration.FiniteDuration

package object model {

  /**
   * Command signalling that actor should push accumulated metrics to extension
   */
  private[scalac] case object PushMetrics

  type MailboxTime = Long // opaque
  object MailboxTime {
    def apply(time: FiniteDuration): MailboxTime =
      time.toMillis
  }

}
