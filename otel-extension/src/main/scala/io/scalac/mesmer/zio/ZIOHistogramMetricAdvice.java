package io.scalac.mesmer.zio;

import net.bytebuddy.asm.Advice;
import zio.metrics.Metric;

public class ZIOHistogramMetricAdvice {

  @Advice.OnMethodExit
  public static void histogram(
      @Advice.Argument(0) String metricName, @Advice.Return Metric histogram) {
    ZIOMetricsInstrumenter.registerAsyncHistogramForZIO(metricName, histogram);
  }
}
