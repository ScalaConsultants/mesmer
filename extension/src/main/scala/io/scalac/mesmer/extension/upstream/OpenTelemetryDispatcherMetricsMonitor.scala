package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.common
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaDispatcherModule
import io.scalac.mesmer.extension.metric.DispatcherStaticMetricsMonitor
import io.scalac.mesmer.extension.metric.Histogram
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.upstream.OpenTelemetryDispatcherMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry.SynchronousInstrumentFactory

object OpenTelemetryDispatcherMetricsMonitor {

  final case class MetricNames(
    minThreads: String,
    maxThreads: String,
    parallelismFactor: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] {
    val defaultConfig: MetricNames =
      MetricNames(
        "akka_dispatcher_threads_min",
        "akka_dispatcher_threads_max",
        "akka_dispatcher_parallelism_factor"
      )

    protected val mesmerConfig: String = "metrics.dispatcher-metrics"

    protected def extractFromConfig(config: Config): MetricNames = {

      val minThreads = config
        .tryValue("min-threads")(_.getString)
        .getOrElse(defaultConfig.minThreads)
      val maxThreads = config
        .tryValue("max-threads")(_.getString)
        .getOrElse(defaultConfig.maxThreads)
      val parallelismFactor = config
        .tryValue("parallelism-factor")(_.getString)
        .getOrElse(defaultConfig.parallelismFactor)

      MetricNames(
        minThreads = minThreads,
        maxThreads = maxThreads,
        parallelismFactor = parallelismFactor
      )
    }

  }

  def apply(
    meter: Meter,
    moduleConfig: AkkaDispatcherModule.AkkaDispatcherMinMaxThreadsConfigMetricsDef[Boolean],
    config: Config
  ): OpenTelemetryDispatcherMetricsMonitor =
    new OpenTelemetryDispatcherMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))
}

final class OpenTelemetryDispatcherMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaDispatcherModule.AkkaDispatcherMinMaxThreadsConfigMetricsDef[Boolean],
  metricNames: MetricNames
) extends DispatcherStaticMetricsMonitor {

  import DispatcherStaticMetricsMonitor._

  private lazy val minThreadsHistogram = meter
    .histogramBuilder(metricNames.minThreads)
    .ofLongs()
    .setDescription("Minimum number of executor threads")
    .build()

  private lazy val maxThreadsHistogram = meter
    .histogramBuilder(metricNames.maxThreads)
    .ofLongs()
    .setDescription("Maximum number of executor threads")
    .build()

  private lazy val parallelismFactorHistogram = meter
    .histogramBuilder(metricNames.parallelismFactor)
    .ofLongs()
    .setDescription("Factor for calculating number of threads from available processors")
    .build()

  def bind(attributes: Attributes): BoundMonitor = new DispatcherMetricsBoundMonitor(attributes)

  class DispatcherMetricsBoundMonitor(attributes: Attributes)
      extends opentelemetry.Synchronized(meter)
      with BoundMonitor
      with SynchronousInstrumentFactory
      with RegisterRoot {

    protected val otAttributes: common.Attributes = AttributesFactory.of(attributes.serialize)

    lazy val minThreads: Histogram[Long] with Instrument[Long] =
      if (moduleConfig.minThreads) histogram(minThreadsHistogram, otAttributes) else noopHistogram[Long]

    lazy val maxThreads: Histogram[Long] with Instrument[Long] =
      if (moduleConfig.maxThreads) histogram(maxThreadsHistogram, otAttributes) else noopHistogram[Long]

    lazy val parallelismFactor: Histogram[Long] with Instrument[Long] =
      if (moduleConfig.parallelismFactor) histogram(parallelismFactorHistogram, otAttributes) else noopHistogram[Long]

  }
}
