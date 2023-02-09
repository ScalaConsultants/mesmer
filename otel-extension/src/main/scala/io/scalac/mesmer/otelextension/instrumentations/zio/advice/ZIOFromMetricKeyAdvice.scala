package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.api.util.VirtualField
import net.bytebuddy.asm.Advice
import zio.metrics.Metric
import zio.metrics.Metric.Counter
import zio.metrics.Metric.Gauge
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType

import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics
import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics._

object ZIOFromMetricKeyAdvice {

  @Advice.OnMethodExit
  def fromMetricKeyExit[Type <: MetricKeyType, _](
    @Advice.Argument(0) key: MetricKey[Type],
    @Advice.Return metric: Metric[Type, _, _]
  ): Unit = {
    val attributes = ZIOMetrics.buildAttributes(key.tags)

    key.keyType match {
      case _: MetricKeyType.Counter =>
        registerCounterAsyncMetric(key.name, metric.asInstanceOf[Counter[_]], attributes)
      case _: MetricKeyType.Gauge =>
        registerGaugeAsyncMetric(key.name, metric.asInstanceOf[Gauge[_]], attributes)
      case _ => ()
    }

    VirtualField
      .find(classOf[Metric[Type, _, _]], classOf[String])
      .set(metric, key.name)

    VirtualField
      .find(classOf[Metric[Type, _, _]], classOf[Attributes])
      .set(metric, attributes)
  }
}
