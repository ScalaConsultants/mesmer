package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import net.bytebuddy.asm.Advice
import zio.Executor

import io.scalac.mesmer.otelextension.instrumentations.zio.ExecutorMetricsProvider

object ZIOExecutorAdvice {
  @Advice.OnMethodExit
  def constructExecutor(@Advice.This executor: Executor): Unit =
    ExecutorMetricsProvider.registerExecutorMetrics(executor)

}
