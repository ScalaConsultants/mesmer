package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaDispatcherModule

object DispatcherStaticMetricsMonitor {
  final case class Attributes(node: Option[Node], executor: Executor, minThreads: MinThreads, maxThreads: MaxThreads, parallelismFactor: Parallelism) extends AttributesSerializable {
    val serialize: RawAttributes = node.serialize ++ executor.serialize ++ minThreads.serialize ++ maxThreads.serialize ++ parallelismFactor.serialize
  }

  trait BoundMonitor extends Bound with AkkaDispatcherModule.Metrics[MetricObserver[Long, Attributes]] {}
}
