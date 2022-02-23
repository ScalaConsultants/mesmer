package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter
import io.scalac.mesmer.core.config.{Configuration, MesmerConfiguration}
import io.scalac.mesmer.core.module.AkkaStreamModule
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.metric.StreamOperatorMetricsMonitor
import io.scalac.mesmer.extension.metric.StreamOperatorMetricsMonitor.Attributes
import io.scalac.mesmer.extension.metric.StreamOperatorMetricsMonitor.BoundMonitor
import io.scalac.mesmer.extension.upstream.OpenTelemetryStreamOperatorMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryStreamOperatorMetricsMonitor {
  case class MetricNames(operatorProcessed: String, connections: String, runningOperators: String, demand: String)

  object MetricNames extends MesmerConfiguration[MetricNames] with Configuration  {

    protected val mesmerConfig: String = "metrics.stream-metrics"

    val defaultConfig: MetricNames = MetricNames(
      "akka_streams_operator_processed_total",
      "akka_streams_operator_connections",
      "akka_streams_running_operators",
      "akka_streams_operator_demand"
    )

    protected def extractFromConfig(config: Config): MetricNames = {
      val operatorProcessed = config
        .tryValue("operator-processed")(_.getString)
        .getOrElse(defaultConfig.operatorProcessed)

      val operatorConnections = config
        .tryValue("operator-connections")(_.getString)
        .getOrElse(defaultConfig.connections)

      val runningOperators = config
        .tryValue("running-operators")(_.getString)
        .getOrElse(defaultConfig.runningOperators)

      val demand = config
        .tryValue("operator-demand")(_.getString)
        .getOrElse(defaultConfig.demand)

      MetricNames(operatorProcessed, operatorConnections, runningOperators, demand)
    }

  }

  def apply(
    meter: Meter,
    moduleConfig: AkkaStreamModule.StreamOperatorMetricsDef[Boolean],
    config: Config
  ): OpenTelemetryStreamOperatorMetricsMonitor =
    new OpenTelemetryStreamOperatorMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))
}

final class OpenTelemetryStreamOperatorMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaStreamModule.StreamOperatorMetricsDef[Boolean],
  metricNames: MetricNames
) extends StreamOperatorMetricsMonitor {

  private lazy val processedMessageAdapter = new LongSumObserverBuilderAdapter[Attributes](
    meter
      .counterBuilder(metricNames.operatorProcessed)
      .setDescription("Amount of messages process by operator")
  )

  private lazy val operatorsAdapter = new GaugeBuilderAdapter[Attributes](
    meter
      .gaugeBuilder(metricNames.runningOperators)
      .ofLongs()
      .setDescription("Amount of operators in a system")
  )

  private lazy val demandAdapter = new LongSumObserverBuilderAdapter[Attributes](
    meter
      .counterBuilder(metricNames.demand)
      .setDescription("Amount of messages demanded by operator")
  )

  def bind(): StreamOperatorMetricsMonitor.BoundMonitor = new BoundMonitor with RegisterRoot {

    lazy val processedMessages: MetricObserver[Long, Attributes] =
      if (moduleConfig.processedMessages) processedMessageAdapter.createObserver(this) else MetricObserver.noop

    lazy val operators: MetricObserver[Long, Attributes] =
      if (moduleConfig.operators) operatorsAdapter.createObserver(this) else MetricObserver.noop

    lazy val demand: MetricObserver[Long, Attributes] =
      if (moduleConfig.demand) demandAdapter.createObserver(this) else MetricObserver.noop
  }
}
