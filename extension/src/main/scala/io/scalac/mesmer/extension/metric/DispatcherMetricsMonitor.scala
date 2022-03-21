package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaDispatcherModule._

object DispatcherMetricsMonitor {
  final case class Attributes(
    node: Option[Node],
    executorType: ExecutorType
  ) extends AttributesSerializable {
    val serialize: RawAttributes =
      node.serialize ++ executorType.serialize
  }

  trait BoundMonitor
      extends AkkaDispatcherMinMaxThreadsConfigMetricsDef[Metric[Long]]
      with AkkaDispatcherThreadCountMetricsDef[Metric[Long]]
      with Synchronized
      with Bound {
    def minThreads: Histogram[Long] with Instrument[Long]
    def maxThreads: Histogram[Long] with Instrument[Long]
    def parallelismFactor: Histogram[Long] with Instrument[Long]
    def activeThreads: Histogram[Long] with Instrument[Long]
    def totalThreads: Histogram[Long] with Instrument[Long]
  }
}
