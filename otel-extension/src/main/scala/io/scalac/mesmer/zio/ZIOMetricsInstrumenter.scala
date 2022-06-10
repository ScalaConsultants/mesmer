package io.scalac.mesmer.zio

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.ObservableLongGauge
import zio._

object ZIOMetricsInstrumenter {

  def trackZIOFiberCount[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] = {
    val runtime = Runtime.default
    for {
      supervisor       <- Supervisor.track(true)
      supervisedEffect <- zio.supervised(supervisor).fork
      _ = registerFiberCountGauge(supervisor, runtime)
      appResult <- supervisedEffect.join
    } yield appResult
  }

  def registerFiberCountGauge[R, E, A](
    supervisor: Supervisor[Chunk[Fiber.Runtime[_, _]]],
    runtime: Runtime[_]
  ): ObservableLongGauge = {
    val meterName = "mesmer"

    GlobalOpenTelemetry
      .getMeter(meterName)
      .gaugeBuilder("mesmer.zio.fiber-count")
      .ofLongs()
      .buildWithCallback { measurement =>
        val fiberCount = supervisor.value.map(_.length)
        // TODO: Is there a nicer way to do this???
        val count = runtime.unsafeRun(fiberCount)
        measurement.record(count)
      }
  }
}
