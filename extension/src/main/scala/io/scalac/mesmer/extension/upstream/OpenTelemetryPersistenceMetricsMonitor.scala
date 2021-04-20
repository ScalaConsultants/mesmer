package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.extension.metric.Counter
import io.scalac.mesmer.extension.metric.MetricRecorder
import io.scalac.mesmer.extension.metric.PersistenceMetricsMonitor
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.upstream.OpenTelemetryPersistenceMetricsMonitor._
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryPersistenceMetricsMonitor {
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
        "akka_persistence_recovery_time",
        "akka_persistence_recovery_total",
        "akka_persistence_persistent_event",
        "akka_persistence_persistent_event_total",
        "akka_persistence_snapshot_total"
      )

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.mesmer.extension.config.ConfigurationUtils._
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
  def apply(meter: Meter, config: Config): OpenTelemetryPersistenceMetricsMonitor =
    new OpenTelemetryPersistenceMetricsMonitor(meter, MetricNames.fromConfig(config))
}

class OpenTelemetryPersistenceMetricsMonitor(meter: Meter, metricNames: MetricNames) extends PersistenceMetricsMonitor {
  import PersistenceMetricsMonitor._

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

  def bind(labels: Labels): BoundMonitor = new OpenTelemetryBoundMonitor(labels)

  class OpenTelemetryBoundMonitor(labels: Labels)
      extends BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {
    private val openTelemetryLabels = LabelsFactory.of(labels.serialize)

    lazy val recoveryTime: MetricRecorder[Long] =
      metricRecorder(recoveryTimeRecorder, openTelemetryLabels).register(this)

    lazy val persistentEvent: MetricRecorder[Long] =
      metricRecorder(persistentEventRecorder, openTelemetryLabels).register(this)

    lazy val persistentEventTotal: Counter[Long] =
      counter(persistentEventTotalCounter, openTelemetryLabels).register(this)

    lazy val snapshot: Counter[Long] =
      counter(snapshotCounter, openTelemetryLabels).register(this)

    lazy val recoveryTotal: Counter[Long] =
      counter(recoveryTotalCounter, openTelemetryLabels).register(this)

  }
}
