package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaDispatcherModule
import io.scalac.mesmer.extension.metric.DispatcherStaticMetricsMonitor
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.upstream.OpenTelemetryDispatcherMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry.GaugeBuilderAdapter
import io.scalac.mesmer.extension.upstream.opentelemetry.SynchronousInstrumentFactory

object OpenTelemetryDispatcherMetricsMonitor {

  final case class MetricNames(
    executor: String,
    minThreads: String,
    maxThreads: String,
    parallelismFactor: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] {
    val defaultConfig: MetricNames =
      MetricNames("akka_dispatcher_executor", "akka_dispatcher_min_threads", "akka_dispatcher_max_threads", "akka_dispatcher_parallelism_factor")

    protected val mesmerConfig: String = "metrics.dispatcher-metrics"

    protected def extractFromConfig(config: Config): MetricNames = {

      val executor = config
        .tryValue("executor")(_.getString)
        .getOrElse(defaultConfig.executor)
      val minThreads = config
        .tryValue("min-threads")(_.getString)
        .getOrElse(defaultConfig.minThreads)
      val maxThreads = config
        .tryValue("max-threads")(_.getString)
        .getOrElse(defaultConfig.maxThreads)
      val parallelismFactor = config
        .tryValue("parallelism-factor")(_.getString)
        .getOrElse(defaultConfig.parallelismFactor)

      MetricNames(executor = executor, minThreads = minThreads, maxThreads = maxThreads, parallelismFactor = parallelismFactor)
    }

  }

  def apply(
             meter: Meter,
             moduleConfig: AkkaDispatcherModule.AkkaDispatcherConfigMetricsDef[Boolean],
             config: Config
           ): OpenTelemetryDispatcherMetricsMonitor =
    new OpenTelemetryDispatcherMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))
}

final class OpenTelemetryDispatcherMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaDispatcherModule.AkkaDispatcherConfigMetricsDef[Boolean],
  metricNames: MetricNames
) extends DispatcherStaticMetricsMonitor {

  import DispatcherStaticMetricsMonitor._

  private lazy val minThreadsObserver = new GaugeBuilderAdapter[DispatcherStaticMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.minThreads)
      .ofLongs()
      .setDescription("Minimum number of executor threads")
  )

  private lazy val maxThreadsObserver = new GaugeBuilderAdapter[DispatcherStaticMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.maxThreads)
      .ofLongs()
      .setDescription("Maximum number of executor threads")
  )

  private lazy val parallelismFactorObserver = new GaugeBuilderAdapter[DispatcherStaticMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.parallelismFactor)
      .ofLongs()
      .setDescription("Factor for calculating number of threads from available processors")
  )

  def bind(): BoundMonitor = new DispatcherMetricsBoundMonitor

  class DispatcherMetricsBoundMonitor extends DispatcherStaticMetricsMonitor.BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {

    val minThreads: MetricObserver[Long, DispatcherStaticMetricsMonitor.Attributes] =
    if (moduleConfig.minThreads) minThreadsObserver.createObserver(this) else MetricObserver.noop

    val maxThreads: MetricObserver[Long, DispatcherStaticMetricsMonitor.Attributes] =
      if (moduleConfig.maxThreads) maxThreadsObserver.createObserver(this) else MetricObserver.noop

    val parallelismFactor: MetricObserver[Long, DispatcherStaticMetricsMonitor.Attributes] =
      if (moduleConfig.parallelismFactor) parallelismFactorObserver.createObserver(this) else MetricObserver.noop

  }
}