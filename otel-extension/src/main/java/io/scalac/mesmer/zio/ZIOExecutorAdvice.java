package io.scalac.mesmer.zio;

import net.bytebuddy.asm.Advice;
import zio.Executor;

public class ZIOExecutorAdvice {

  @Advice.OnMethodExit
  static void construction(@Advice.This Executor executor) {

    System.out.println("Imma firin' an executor: " + executor.toString());

    ZIOMetricsInstrumenter.registerExecutionMetrics(executor);
  }
}
