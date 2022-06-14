package io.scalac.mesmer.zio

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.ObservableLongGauge
import zio._

object ZIOMetricsInstrumenter {

  val meterName = "mesmer"

  val meter = GlobalOpenTelemetry
    .getMeter(meterName)

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
  ): ObservableLongGauge =
    meter
      .gaugeBuilder("mesmer.zio.fiber-count")
      .ofLongs()
      .buildWithCallback { measurement =>
        val fiberCount: ZIO[Any, Nothing, Int] = supervisor.value.map(_.length)
        // TODO: Is there a nicer way to do this???
        val count = runtime.unsafeRun(fiberCount)
        measurement.record(count)
      }

  def setFiberSupervisor[R](newRuntime: zio.Runtime[R]): Unit = {
    val supervisor: Supervisor[Chunk[Fiber.Runtime[Any, Any]]] = newRuntime.unsafeRun(Supervisor.track(true))
    println("WORKING")
    Runtime.addSupervisor(supervisor)
    ZIOMetricsInstrumenter.registerFiberCountGauge(supervisor, newRuntime)
  }

  def registerExecutionMetrics(runtime: Runtime[_]): Unit = {
    val maybeMetrics  = runtime.executor.unsafeMetrics
    val zioMetricName = (suffix: String) => s"mesmer.zio.execution.$suffix"

    registerOtelGauge(zioMetricName("workers_count"), maybeMetrics.map(_.workersCount))
    registerOtelGauge(zioMetricName("concurrency"), maybeMetrics.map(_.concurrency))
    registerOtelGauge(zioMetricName("executor_capacity"), maybeMetrics.map(_.capacity))
    registerOtelGauge(zioMetricName("task_queue_size"), maybeMetrics.map(_.size))
    registerOtelGauge(zioMetricName("enqueued_count"), maybeMetrics.map(_.enqueuedCount))
    registerOtelGauge(zioMetricName("dequeued_count"), maybeMetrics.map(_.dequeuedCount))
  }

  def registerOtelGauge(name: String, metricAccessor: => Option[Long]) =
    meter
      .gaugeBuilder(name)
      .ofLongs()
      .buildWithCallback { measurement =>
        metricAccessor
          .fold(println("Didn't find the metric: $name"))(measurement.record)
      }
}
