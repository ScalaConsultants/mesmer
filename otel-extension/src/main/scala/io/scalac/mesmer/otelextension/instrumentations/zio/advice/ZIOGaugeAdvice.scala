package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import net.bytebuddy.asm.Advice
import zio.metrics.Metric.Gauge

import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics

object ZIOGaugeAdvice {
  @Advice.OnMethodExit
  def counter(@Advice.Argument(0) zioMetricName: String, @Advice.Return gauge: Gauge[_]): Unit =
    ZIOMetrics.registerGaugeAsyncMetric(zioMetricName, gauge)
}
