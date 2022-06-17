package io.scalac.mesmer.zio;

import net.bytebuddy.asm.Advice;
import zio.Runtime;

public class ZioRuntimeJavaAdvice2 {

  @Advice.OnMethodExit
  public static <R> Runtime<R> apply(@Advice.Return(readOnly = false) Runtime<R> newRuntime) {
    ZIOMetricsInstrumenter.registerExecutionMetrics(newRuntime);
    ZIOMetricsInstrumenter.setFiberSupervisor(newRuntime);
    ZIOMetricsInstrumenter.superviseLikeZMXDoes(newRuntime);
    return newRuntime;
  }
}
