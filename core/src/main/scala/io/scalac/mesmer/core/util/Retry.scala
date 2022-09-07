package io.scalac.mesmer.core.util

import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Retry {
  @annotation.tailrec
  def retry[T](n: Int, pause: FiniteDuration)(fn: => T): Try[T] =
    Try(fn) match {
      case Success(value) => Success(value)
      case Failure(_) if n > 1 =>
        Thread.sleep(pause.toMillis)
        retry(n - 1, pause)(fn)
      case Failure(exception) => Failure(exception)
    }

  /**
   * Same as retry but adds an additional precondition check, eg. waiting for another extension to register.
   */
  @annotation.tailrec
  def retryWithPrecondition[T](n: Int, pause: FiniteDuration)(precondition: => Boolean)(fn: => T): Try[T] =
    if (precondition) {
      retry(n, pause)(fn)
    } else {
      Thread.sleep(pause.toMillis)
      retryWithPrecondition(n - 1, pause)(precondition)(fn)
    }
}
