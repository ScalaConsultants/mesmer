package io.scalac.mesmer.zio;

import net.bytebuddy.asm.Advice;
import zio.metrics.Metric;

public class ZIOCounterMetricAdvice {

  @Advice.OnMethodExit
  public static void counter(@Advice.Argument(0) String metricName, @Advice.Return Metric counter) {
    ZIOMetricsInstrumenter.registerAsyncCounterForZIOMetrics(metricName, counter);
  }
}
