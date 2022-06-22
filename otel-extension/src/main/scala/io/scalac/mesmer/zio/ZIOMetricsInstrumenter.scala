package io.scalac.mesmer.zio

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import zio._
import zio.metrics.Metric
import zio.metrics.MetricState

object ZIOMetricsInstrumenter {

  private val meterName = "mesmer"

  private val meter: Meter = GlobalOpenTelemetry.getMeter(meterName)

  def registerExecutionMetrics(runtime: Runtime[_], threadId: Long): Unit = {
    println("Registering execution maybeWorkersCount")

    val maybeMetrics  = runtime.executor.unsafeMetrics
    val zioMetricName = (suffix: String) => s"mesmer.zio.execution.$suffix"

    registerOtelGauge(zioMetricName("workers_count"), maybeMetrics.map(_.workersCount), threadId)
    registerOtelGauge(zioMetricName("concurrency"), maybeMetrics.map(_.concurrency), threadId)
    registerOtelGauge(zioMetricName("executor_capacity"), maybeMetrics.map(_.capacity), threadId)
    registerOtelGauge(zioMetricName("task_queue_size"), maybeMetrics.map(_.size), threadId)
    registerOtelGauge(zioMetricName("enqueued_count"), maybeMetrics.map(_.enqueuedCount), threadId)
    registerOtelGauge(zioMetricName("dequeued_count"), maybeMetrics.map(_.dequeuedCount), threadId)
  }

  // There we have the async instruments for corresponding ZIO instruments.
  // We should also have a list of maybeWorkersCount that we don't want to instrument.
  // This is based on the following assumptions:
  // - if a user uses mesmer with with their zio app, they for sure want to instrument it. So let's give them all we have at the start.
  // - if some maybeWorkersCount are problematic, redundant, they can be excluded from otel collection.
  // - ofc still, the user can turn off maybeWorkersCount collection completely by using the `mesmer.zio.maybeWorkersCount.enabled` config flag

  // Opt-in is a worse solution imho:
  // - it's not "all the maybeWorkersCount outside the box"
  // - it requires some deeper knowledge about the whole app
  def registerAsyncCounterForZIOMetrics(
    metricName: String,
    metric: Metric[_, _, MetricState.Counter],
    threadId: Long
  ): Unit = {
    println(s"Registering an OTEL counter for $metricName.")

    // We can reach the otel config for a list of maybeWorkersCount that we wish not to report with otel.
    meter.counterBuilder(metricName).buildWithCallback { measurement =>
      val value: MetricState.Counter = Runtime.default.unsafeRun(metric.value)
      val attributes = Attributes
        .builder()
        .put("creating_thread_id", threadId)
        .put("current_thread_id", Thread.currentThread().getId)
        .build()

      measurement.record(value.count.longValue())
    }
  }

  def registerAsyncGaugeForZIOMetrics(
    metricName: String,
    metric: Metric[_, _, MetricState.Gauge],
    threadId: Long
  ): Unit = {
    println(s"Registering an OTEL gauge for $metricName.")

    registerOtelGauge(metricName, Some(Runtime.default.unsafeRun(metric.value).value.longValue()), threadId)
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
    Runtime.default.unsafeRun(repeated.fork)
  }

  private def registerOtelGauge(name: String, metricAccessor: => Option[Long], threadId: Long): ObservableLongGauge =
    meter
      .gaugeBuilder(name)
      .ofLongs()
      .buildWithCallback { measurement =>
        val atr = Attributes
          .builder()
          .put("creating_thread_id", threadId)
          .put("current_thread_id", Thread.currentThread().getId)
          .build()
        metricAccessor.fold(println(s"Didn't find the metric: $name"))(measurement.record)
      }
}
