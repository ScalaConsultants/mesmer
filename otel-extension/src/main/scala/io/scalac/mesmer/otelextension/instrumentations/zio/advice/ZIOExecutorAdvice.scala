package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import net.bytebuddy.asm.Advice
import zio.Executor

object ZIOExecutorAdvice {
  @Advice.OnMethodExit
  def constructExecutor(@Advice.This executor: Executor): Unit =
    println(s"Executor construction. ${executor.toString}")
}
