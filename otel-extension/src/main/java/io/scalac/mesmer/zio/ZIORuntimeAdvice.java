package io.scalac.mesmer.zio;

import net.bytebuddy.asm.Advice;
import zio.Runtime;

public class ZIORuntimeAdvice {

  @Advice.OnMethodExit
  public static <R> Runtime<R> apply(@Advice.Return(readOnly = false) Runtime<R> newRuntime) {
    ZIOMetricsInstrumenter.registerExecutionMetrics(newRuntime, Thread.currentThread().getId());
    return newRuntime;
  }
}
