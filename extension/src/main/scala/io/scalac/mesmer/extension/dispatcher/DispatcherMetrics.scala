package io.scalac.mesmer.extension.dispatcher

case class DispatcherMetrics(
  minThreads: Option[Long],
  maxThreads: Option[Long],
  parallelismFactor: Option[Double]
)

object DispatcherMetrics {
  val empty: DispatcherMetrics = new DispatcherMetrics(None, None, None)
}
