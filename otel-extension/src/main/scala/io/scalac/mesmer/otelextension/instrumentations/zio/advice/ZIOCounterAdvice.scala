package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import net.bytebuddy.asm.Advice
import zio.metrics.Metric.Counter

import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics

object ZIOCounterAdvice {
  @Advice.OnMethodExit
  def counter(@Advice.Argument(0) zioMetricName: String, @Advice.Return counter: Counter[_]): Unit =
    ZIOMetrics.registerCounterAsyncMetric(zioMetricName, counter)
}
