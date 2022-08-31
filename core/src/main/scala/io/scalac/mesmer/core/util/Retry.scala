package io.scalac.mesmer.core.util

import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Retry {
  @annotation.tailrec
  def retryWithPauses[T](n: Int, pause: FiniteDuration)(fn: => T): Try[T] =
    Try(fn) match {
      case Success(value) => Success(value)
      case Failure(_) if n > 1 =>
        Thread.sleep(pause.toMillis)
        retryWithPauses(n - 1, pause)(fn)
      case Failure(exception) => Failure(exception)
    }
}
