package io.scalac.mesmer.otelextension.instrumentations.zio

import java.util.function.Consumer

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongGauge
import io.opentelemetry.api.metrics.ObservableLongMeasurement
import zio.Executor
import zio.Unsafe
import zio.internal.ExecutionMetrics

object ZIOExecutorMetrics {

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  private val executionMetricName: String => String = (suffix: String) => s"mesmer_zio_executor_$suffix"

  def registerExecutorMetrics(executor: Executor): Unit =
    Unsafe.unsafe { implicit u =>
      val executorAttributes = Attributes.builder().put("executor", executor.toString).build()

      def extract(metricExtractor: ExecutionMetrics => Long): Consumer[ObservableLongMeasurement] =
        (measurement: ObservableLongMeasurement) =>
          executor.metrics.map(metrics => measurement.record(metricExtractor(metrics), executorAttributes))

      registerAsyncLongGauge(
        executionMetricName("worker_count"),
        "Count",
        "The number of current live worker threads."
      )(extract(_.workersCount))

      registerAsyncLongGauge(
        executionMetricName("concurrency"),
        "Concurrency level",
        "The concurrency level of the executor."
      )(extract(_.concurrency))

      registerAsyncLongGauge(
        executionMetricName("size"),
        "Task count",
        "The number of tasks remaining to be executed."
      )(extract(_.size))

      registerAsyncLongGauge(
        executionMetricName("capacity"),
        "Capacity",
        "The capacity of the executor."
      )(extract(_.capacity))

      registerAsyncLongGauge(
        executionMetricName("dequeued_count"),
        "Task count",
        "The number of tasks that have been dequeued, over all time."
      )(extract(_.dequeuedCount))

      registerAsyncLongGauge(
        executionMetricName("enqueued_count"),
        "Task count",
        "The number of tasks that have been enqueued, over all time."
      )(extract(_.enqueuedCount))
    }

  private def registerAsyncLongGauge(name: String, unit: String, description: String)(
    callback: Consumer[ObservableLongMeasurement]
  ): ObservableLongGauge =
    meter
      .gaugeBuilder(name)
      .ofLongs()
      .setUnit(unit)
      .setDescription(description)
      .buildWithCallback(callback)
}
