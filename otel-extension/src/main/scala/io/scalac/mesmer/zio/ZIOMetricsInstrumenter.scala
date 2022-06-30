package io.scalac.mesmer.zio

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import zio.Console
import zio.Executor
import zio.Runtime
import zio.Schedule
import zio.Unsafe
import zio.durationInt
import zio.metrics.Metric
import zio.metrics.MetricState

object ZIOMetricsInstrumenter {

  private val meterName = "mesmer"

  private val meter: Meter = GlobalOpenTelemetry.getMeter(meterName)

  def registerExecutionMetrics(executor: Executor): Unit = {
    try
      Unsafe.unsafe { implicit u =>
        val maybeMetrics = executor.metrics
        val attributes =
          Attributes.builder().put("executor", executor.toString).build() // This is just to distinguish them
        val zioMetricName = (suffix: String) => s"mesmer.zio.execution.$suffix"

        registerOtelGauge(zioMetricName("workers_count"), maybeMetrics.map(_.workersCount), attributes)
        registerOtelGauge(zioMetricName("concurrency"), maybeMetrics.map(_.concurrency), attributes)
        registerOtelGauge(zioMetricName("executor_capacity"), maybeMetrics.map(_.capacity), attributes)
        registerOtelGauge(zioMetricName("task_queue_size"), maybeMetrics.map(_.size), attributes)
        registerOtelGauge(zioMetricName("enqueued_count"), maybeMetrics.map(_.enqueuedCount), attributes)
        registerOtelGauge(zioMetricName("dequeued_count"), maybeMetrics.map(_.dequeuedCount), attributes)
      }
  }

  // There we have the async instruments for corresponding ZIO instruments.
  // We should also have a list of metrics that we don't want to instrument.
  // This is based on the following assumptions:
  // - if a user uses mesmer with with their zio app, they for sure want to instrument it. So let's give them all we have at the start.
  // - if some metrics are problematic, redundant, they can be excluded from otel collection.
  // - ofc still, the user can turn off metrics collection completely by using the `mesmer.zio.metrics.enabled` config flag

  // Opt-in is a worse solution imho:
  // - it's not "all the maybeWorkersCount outside the box"
  // - it requires some deeper knowledge about the whole app
  def registerAsyncCounterForZIOMetrics(
    metricName: String,
    metric: Metric[_, _, MetricState.Counter]
  ): Unit = {
    println(s"Registering an OTEL counter for $metricName.")

    // We can reach the otel config for a list of maybeWorkersCount that we wish not to report with otel.
    meter.counterBuilder(metricName).buildWithCallback { measurement =>
      Unsafe.unsafe { implicit u =>
        val value = Runtime.default.unsafe.run(metric.value).getOrThrowFiberFailure()
        measurement.record(value.count.longValue())
      }
    }
  }

  def registerAsyncGaugeForZIOMetrics(
    metricName: String,
    metric: Metric[_, _, MetricState.Gauge],
    threadId: Long
  ): Unit = {
    println(s"Registering an OTEL gauge for $metricName.")

    registerOtelGauge(
      metricName,
      Some(
        Unsafe.unsafe { implicit u =>
          Runtime.default.unsafe.run(metric.value).getOrThrowFiberFailure().value.longValue()
        }
      ),
      Attributes.empty()
    )
  }

  // This just proves that I can have a fiber dedicated for reading from the ZIO histogram to fetch the values.
  // Then we can pass them to a synchronous otel histogram (as there is no async version of it).
  // Note that the way I'm extracting the values is probably wrong but I still get the data in Prometheus
  // which is the thing I wanted to test.
  def registerAsyncHistogramForZIO(metricName: String, metric: Metric[_, _, MetricState.Histogram]): Unit = {
    println(s"Registering an OTEL histogram for $metricName.")

    // There's no async histogram in OTEL yet. Maybe it's a good idea to wrap the existing synchronous instrument
    // with a custom callback to achieve the same result?
    val histogram: LongHistogram = meter.histogramBuilder(metricName).ofLongs().build()

    val metricsPeriodicCollection = for {
      value <- metric.value
      // TODO: probably the wrong way to do this??
      _ = value.buckets.map(_._2).map(histogram.record)
      _ <- Console.printLine(value.buckets.toList)
      _ <- Console.printLine("I'm alive!!!!")
    } yield ()

    val repeated = metricsPeriodicCollection repeat Schedule.fixed(1.second)

    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(repeated.fork).getOrThrowFiberFailure()
    }
  }

  private def registerOtelGauge(
    name: String,
    metricAccessor: => Option[Long],
    attributes: Attributes
  ): ObservableLongGauge =
    meter
      .gaugeBuilder(name)
      .ofLongs()
      .buildWithCallback { measurement =>
        metricAccessor.fold(println(s"Didn't find the metric: $name"))(measurement.record(_, attributes))
      }
}
