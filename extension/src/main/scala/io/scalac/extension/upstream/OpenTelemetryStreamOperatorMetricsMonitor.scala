package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.StreamOperatorMetricsMonitor.{ BoundMonitor, Labels }
import io.scalac.extension.metric.{ LazyMetricObserver, StreamOperatorMetricsMonitor }
import io.scalac.extension.upstream.OpenTelemetryStreamOperatorMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryStreamOperatorMetricsMonitor {
  case class MetricNames(operatorProcessed: String, connections: String, runningOperators: String)

  object MetricNames {
    private val defaults: MetricNames =
      MetricNames(
        "akka_streams_operator_processed_total",
        "akka_streams_operator_connections",
        "akka_streams_running_operators"
      )

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._

      config.tryValue("io.scalac.akka-monitoring.metrics.streams-metrics")(_.getConfig).map { streamMetricsConfig =>
        val operatorProcessed = streamMetricsConfig
          .tryValue("operator-processed")(_.getString)
          .getOrElse(defaults.operatorProcessed)

        val operatorConnections = streamMetricsConfig
          .tryValue("operator-connections")(_.getString)
          .getOrElse(defaults.connections)

        val runningOperators = streamMetricsConfig
          .tryValue("running-operators")(_.getString)
          .getOrElse(defaults.runningOperators)

        MetricNames(operatorProcessed, operatorConnections, runningOperators)
      }
    }.getOrElse(defaults)
  }

  def apply(instrumentationName: String, config: Config): OpenTelemetryStreamOperatorMetricsMonitor =
    new OpenTelemetryStreamOperatorMetricsMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryStreamOperatorMetricsMonitor(instrumentationName: String, metricNames: MetricNames) extends StreamOperatorMetricsMonitor {


  private val meter = OpenTelemetry
    .getGlobalMeter(instrumentationName)

  private val processedMessageAdapter = new LazyLongSumObserverBuilderAdapter[Labels](
    meter
      .longSumObserverBuilder(metricNames.operatorProcessed)
      .setDescription("Amount of messages process by operator")
  )

  private val operatorsAdapter = new LazyLongValueObserverBuilderAdapter[Labels](
    meter
      .longValueObserverBuilder(metricNames.runningOperators)
      .setDescription("Amount of operators in a system")
  )

  override def bind(): StreamOperatorMetricsMonitor.BoundMonitor = new BoundMonitor {
    private var unbinds: List[ObserverHandle] = Nil

    override lazy val processedMessages: LazyMetricObserver[Long, Labels] = {
      val (unbind, observer) = processedMessageAdapter.createObserver()
      unbinds ::= unbind
      observer
    }

    override lazy val operators: LazyMetricObserver[Long, Labels] = {
      val (unbind, observer) = operatorsAdapter.createObserver()
      unbinds ::= unbind
      observer
    }

    override def unbind(): Unit = unbinds.foreach(_.unbindObserver())
  }
}
