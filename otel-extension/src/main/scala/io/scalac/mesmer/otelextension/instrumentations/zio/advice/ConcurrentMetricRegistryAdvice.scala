package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryClient
import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryListener
import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryPoller
import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics
import net.bytebuddy.asm.Advice

object ConcurrentMetricRegistryAdvice {
  @Advice.OnMethodExit
  def constructExecutor(@Advice.This metricRegistry: AnyRef): Unit = {
    val client     = new ConcurrentMetricRegistryClient(metricRegistry)
    val zioMetrics = new ZIOMetrics(client)
    new ConcurrentMetricRegistryPoller(client, zioMetrics)
    new ConcurrentMetricRegistryListener(client, zioMetrics)
  }
}
