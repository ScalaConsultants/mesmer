package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaDispatcherModule._

//TODO figure out what should be proper attributes for these dispatcher metrics
object DispatcherStaticMetricsMonitor {
  final case class Attributes(
    node: Option[Node],
    minThreads: MinThreads,
    maxThreads: MaxThreads
  ) extends AttributesSerializable {
    val serialize: RawAttributes =
      node.serialize ++ minThreads.serialize ++ maxThreads.serialize
  }

  trait BoundMonitor extends AkkaDispatcherMinMaxThreadsConfigMetricsDef[Metric[Long]] with Synchronized with Bound {
    def minThreads: Histogram[Long] with Instrument[Long]
    def maxThreads: Histogram[Long] with Instrument[Long]
    def parallelismFactor: Histogram[Long] with Instrument[Long]
  }
}
