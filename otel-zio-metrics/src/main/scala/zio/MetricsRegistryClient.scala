package zio

import zio.internal.metrics._
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType
import zio.metrics.MetricPair

object MetricsRegistryClient {

  def snapshot(): Set[MetricPair.Untyped] =
    metricRegistry.snapshot()(Unsafe.unsafe)

  def get[Type <: MetricKeyType](key: MetricKey[Type]): MetricHook[key.keyType.In, key.keyType.Out] =
    metricRegistry.get(key)(Unsafe.unsafe)
}
