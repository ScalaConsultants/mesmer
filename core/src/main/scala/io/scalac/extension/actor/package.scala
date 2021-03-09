package io.scalac.extension

import scala.concurrent.duration.FiniteDuration

package object actor {

  type MailboxTime = Long
  object MailboxTime {
    def apply(time: FiniteDuration): MailboxTime = time.toMillis
  }

}
