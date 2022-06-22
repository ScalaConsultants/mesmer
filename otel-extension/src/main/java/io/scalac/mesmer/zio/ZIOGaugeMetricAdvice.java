package io.scalac.mesmer.zio;

import net.bytebuddy.asm.Advice;
import zio.metrics.Metric;

public class ZIOGaugeMetricAdvice {

  @Advice.OnMethodExit
  public static void gauge(@Advice.Argument(0) String metricName, @Advice.Return Metric gauge) {
    ZIOMetricsInstrumenter.registerAsyncGaugeForZIOMetrics(
        metricName, gauge, Thread.currentThread().getId());
  }
}
