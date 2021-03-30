package io.scalac.agent.util

import java.time.Duration
import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Success

trait FunctionOps {

  private[FunctionOps] val ignore: Any => Unit = Function.const(())

  protected def futureCallbackLatency[T, R](
    function: T => Future[R],
    consumer: (Long => Unit) = ignore
  )(implicit ec: ExecutionContext): T => Future[R] = input => {
    val before = Instant.now()
    val result = function(input)

    result.andThen { case Success(_) =>
      val millis = Duration.between(before, Instant.now()).toMillis
      consumer(millis)
    }
  }
}

object FunctionOps extends FunctionOps {
  implicit class FutureFunctionOps[T, R](value: T => Future[R]) {
    def latency(
      consumer: Long => Unit
    )(implicit ec: ExecutionContext): T => Future[R] =
      futureCallbackLatency(value, consumer)
  }
}
