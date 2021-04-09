package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter
import io.scalac.extension.metric.{ Counter, MetricRecorder, PersistenceMetricMonitor, RegisterRoot }
import io.scalac.extension.upstream.OpenTelemetryPersistenceMetricMonitor._
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryPersistenceMetricMonitor {
  case class MetricNames(
    recoveryTime: String,
    recoveryTotal: String,
    persistentEvent: String,
    persistentEventTotal: String,
    snapshotTotal: String
  )

  object MetricNames {
    def default: MetricNames =
      MetricNames(
        "recovery_time",
        "recovery_total",
        "persistent_event",
        "persistent_event_total",
        "snapshot_total"
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
          val recoveryTotal = clusterMetricsConfig
            .tryValue("recovery-total")(_.getString)
            .getOrElse(defaultCached.recoveryTotal)
          val persistentEvent = clusterMetricsConfig
            .tryValue("persistent-event")(_.getString)
            .getOrElse(defaultCached.persistentEvent)
          val persistentEventTotal = clusterMetricsConfig
            .tryValue("persistent-event-total")(_.getString)
            .getOrElse(defaultCached.persistentEventTotal)
          val snapshotTotal = clusterMetricsConfig
            .tryValue("snapshot")(_.getString)
            .getOrElse(defaultCached.snapshotTotal)

          MetricNames(recoveryTime, recoveryTotal, persistentEvent, persistentEventTotal, snapshotTotal)
        }
        .getOrElse(defaultCached)
    }
  }
  def apply(meter: Meter, config: Config): OpenTelemetryPersistenceMetricMonitor =
    new OpenTelemetryPersistenceMetricMonitor(meter, MetricNames.fromConfig(config))
}

class OpenTelemetryPersistenceMetricMonitor(meter: Meter, metricNames: MetricNames) extends PersistenceMetricMonitor {
  import PersistenceMetricMonitor._

  private val recoveryTimeRecorder = meter
    .longValueRecorderBuilder(metricNames.recoveryTime)
    .setDescription("Amount of time needed for entity recovery")
    .build()

  private val recoveryTotalCounter = meter
    .longCounterBuilder(metricNames.recoveryTotal)
    .setDescription("Amount of recoveries")
    .build()

  private val persistentEventRecorder = meter
    .longValueRecorderBuilder(metricNames.persistentEvent)
    .setDescription("Amount of time needed for entity to persist events")
    .build()

  private val persistentEventTotalCounter = meter
    .longCounterBuilder(metricNames.persistentEventTotal)
    .setDescription("Amount of persist events")
    .build()

  private val snapshotCounter = meter
    .longCounterBuilder(metricNames.snapshotTotal)
    .setDescription("Amount of snapshots created")
    .build()

  override def bind(labels: Labels): BoundMonitor = new OpenTelemetryBoundMonitor(labels)

  class OpenTelemetryBoundMonitor(labels: Labels)
      extends BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {
    private val openTelemetryLabels = LabelsFactory.of(labels.serialize)

    override lazy val recoveryTime: MetricRecorder[Long] =
      metricRecorder(recoveryTimeRecorder, openTelemetryLabels).register(this)

    override lazy val persistentEvent: MetricRecorder[Long] =
      metricRecorder(persistentEventRecorder, openTelemetryLabels).register(this)

    override lazy val persistentEventTotal: Counter[Long] =
      counter(persistentEventTotalCounter, openTelemetryLabels).register(this)

    override lazy val snapshot: Counter[Long] =
      counter(snapshotCounter, openTelemetryLabels).register(this)

    override lazy val recoveryTotal: Counter[Long] =
      counter(recoveryTotalCounter, openTelemetryLabels).register(this)

  }
}
