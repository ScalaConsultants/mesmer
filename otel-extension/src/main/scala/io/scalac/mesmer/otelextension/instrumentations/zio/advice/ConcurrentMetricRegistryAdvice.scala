package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import net.bytebuddy.asm.Advice

import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryPoller

object ConcurrentMetricRegistryAdvice {
  @Advice.OnMethodExit
  def constructExecutor(): Unit =
    new ConcurrentMetricRegistryPoller
}
