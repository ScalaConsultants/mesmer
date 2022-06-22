package io.scalac.mesmer.zio

import io.opentelemetry.api.GlobalOpenTelemetry
import zio.Fiber

object MesmerFiberInstrumentation {

  private val meter = GlobalOpenTelemetry.getMeter("zio-fibers")

  private val runningFiberCounter   = meter.upDownCounterBuilder("zio.mesmer.running.fibers.count").build()
  private val resumedFiberCounter   = meter.upDownCounterBuilder("zio.mesmer.resumed.fibers.count").build()
  private val suspendedFiberCounter = meter.upDownCounterBuilder("zio.mesmer.suspended.fibers.count").build()
  private val fiberLifetime         = meter.histogramBuilder("zio.mesmer.fiber.lifetimes").build()

  def witnessFiberStart(fiber: Fiber.Runtime[_, _]): Unit =
    runningFiberCounter.add(1)

  def witnessFiberEnd(fiber: Fiber.Runtime[_, _]): Unit = {
    runningFiberCounter.add(-1)

    val endTimeSeconds = java.lang.System.currentTimeMillis() / 1000
    val lifetime       = endTimeSeconds - fiber.id.startTimeSeconds
    fiberLifetime.record(lifetime)

  }

  // TODO: Not a good idea - the data is highly inconsistent
  def witnessFiberSuspension(): Unit = {
    suspendedFiberCounter.add(1)
    resumedFiberCounter.add(-1)
  }

  // TODO: Not a good idea - the data is highly inconsistent
  def witnessFiberResumption(): Unit = {
    resumedFiberCounter.add(1)
    suspendedFiberCounter.add(-1)
  }
}
