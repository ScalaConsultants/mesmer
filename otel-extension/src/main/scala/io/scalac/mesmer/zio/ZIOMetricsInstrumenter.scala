package io.scalac.mesmer.zio

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import zio._
import zio.metrics.Metric
import zio.metrics.MetricState

object ZIOMetricsInstrumenter {

  val meterName = "mesmer"

  val meter: Meter = GlobalOpenTelemetry
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

  def superviseLikeZMXDoes[R](newRuntime: zio.Runtime[R]): Unit =
    meter
      .gaugeBuilder("mesmer.zio.fiber-count")
      .ofLongs()
      .buildWithCallback { measurement =>
        val c = for {
          fibers <- diagnostics.ZMXSupervisor.value
        } yield fibers.size
        val countOfFibers = newRuntime.unsafeRun(c)
        measurement.record(countOfFibers)
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

  def registerAsyncCounterForZIOMetrics(metricName: String, metric: Metric[_, _, MetricState.Counter]): Unit = {
    println(s"Registering an OTEL counter for $metricName.")

    // We can reach the otel config for a list of metrics that we wish not to report with otel.

    meter.counterBuilder(metricName).buildWithCallback { measurement =>
      val value: MetricState.Counter = Runtime.default.unsafeRun(metric.value)
      measurement.record(value.count.longValue())
    }
  }

  def registerAsyncGaugeForZIOMetrics(metricName: String, metric: Metric[_, _, MetricState.Gauge]): Unit = {
    println(s"Registering an OTEL gauge for $metricName.")

    meter.gaugeBuilder(metricName).buildWithCallback { measurement =>
      val value: MetricState.Gauge = Runtime.default.unsafeRun(metric.value)
      measurement.record(value.value)
    }
  }

  def registerAsyncHistogramForZIO(metricName: String, metric: Metric[_, _, MetricState.Histogram]): Unit = {
    println(s"Registering an OTEL histogram for $metricName.")

    // There's no async histogram in OTEL yet. Maybe it's a good idea to wrap the existing synchronous instrument
    // with a custom callback to achieve the same result?
    val histogram: LongHistogram = meter.histogramBuilder(metricName).ofLongs().build()

    val metricsPeriodicCollection = for {
      value <- metric.value
      // TODO: just take the longs and record all??
      _ = value.buckets.map(_._2).map(histogram.record)
      _ <- Console.printLine(value.buckets.toList)
      _ <- Console.printLine("I'm alive!!!!")
    } yield ()

    val repeated = metricsPeriodicCollection repeat Schedule.fixed(1.second)

    // Todo: does this break RT of the instrumented zio app? Is this even a problem in case of metrics?
    Runtime.default.unsafeRun(repeated.fork)
  }

  def registerOtelGauge(name: String, metricAccessor: => Option[Long]): ObservableLongGauge =
    meter
      .gaugeBuilder(name)
      .ofLongs()
      .buildWithCallback { measurement =>
        metricAccessor
          .fold(println(s"Didn't find the metric: $name"))(measurement.record)
      }
}
