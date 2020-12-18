package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.Metric._
import io.scalac.extension.metric.{ MetricRecorder, PersistenceMetricMonitor }
import io.scalac.extension.upstream.OpenTelemetryPersistenceMetricMonitor._

object OpenTelemetryPersistenceMetricMonitor {
  case class MetricNames(
    recoveryTime: String
  )

  object MetricNames {
    def default: MetricNames =
      MetricNames(
        "recovery_time"
      )

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._
      val defaultCached = default

      config
        .tryValue("io.scalac.akka-monitoring.metrics.cluster-metrics")(
          _.getConfig
        )
        .map { clusterMetricsConfig =>
          val recoveryTime = clusterMetricsConfig
            .tryValue("recovery-time")(_.getString)
            .getOrElse(defaultCached.recoveryTime)

          MetricNames(recoveryTime)
        }
        .getOrElse(defaultCached)
    }
  }
  def apply(instrumentationName: String, config: Config): OpenTelemetryPersistenceMetricMonitor =
    new OpenTelemetryPersistenceMetricMonitor(instrumentationName, MetricNames.fromConfig(config))
}

class OpenTelemetryPersistenceMetricMonitor(instrumentationName: String, metricNames: MetricNames)
    extends PersistenceMetricMonitor {
  import PersistenceMetricMonitor._
  private val recoveryTimeRecorder = OpenTelemetry
    .getGlobalMeter(instrumentationName)
    .longValueRecorderBuilder(metricNames.recoveryTime)
    .setDescription("Amount of time needed for entity recovery")
    .build()

  override def bind(labels: Labels): BoundMonitor =
    new BoundMonitor {

      override lazy val recoveryTime: MetricRecorder[Long] =
        recoveryTimeRecorder.bind(labels.toOpenTelemetry).toMetricRecorder()
    }
}
