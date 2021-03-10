package io.scalac.extension

import scala.concurrent.duration.FiniteDuration

package object actor {

  type TimeSpent = Long
  object TimeSpent {
    def apply(time: FiniteDuration): TimeSpent = time.toMillis
  }

}
