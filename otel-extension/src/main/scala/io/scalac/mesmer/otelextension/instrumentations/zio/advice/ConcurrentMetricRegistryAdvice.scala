package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryPoller
import net.bytebuddy.asm.Advice

object ConcurrentMetricRegistryAdvice {
  @Advice.OnMethodExit
  def constructExecutor(): Unit =
    new ConcurrentMetricRegistryPoller
}
