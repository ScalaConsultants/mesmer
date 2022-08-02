package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import net.bytebuddy.asm.Advice
import zio.metrics.Metric
import zio.metrics.Metric.Counter
import zio.metrics.Metric.Gauge
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType

import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics._

object ZIOFromMetricKeyAdvice {

  @Advice.OnMethodExit
  def fromMetricKeyExit[Type <: MetricKeyType, _](
    @Advice.Argument(0) key: MetricKey[Type],
    @Advice.Return metric: Metric[Type, _, _]
  ): Unit =
    key.keyType match {
      case _: MetricKeyType.Counter =>
        registerCounterAsyncMetric(key.name, metric.asInstanceOf[Counter[_]], key.tags)

      case _: MetricKeyType.Gauge =>
        registerGaugeAsyncMetric(key.name, metric.asInstanceOf[Gauge[_]], key.tags)

      case _ => ()
    }

}
