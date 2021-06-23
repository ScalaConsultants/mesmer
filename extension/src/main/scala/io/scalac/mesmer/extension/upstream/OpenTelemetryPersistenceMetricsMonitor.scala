package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter
import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.extension.metric.{ Counter, MetricRecorder, PersistenceMetricsMonitor, RegisterRoot }
import io.scalac.mesmer.extension.upstream.OpenTelemetryPersistenceMetricsMonitor._
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryPersistenceMetricsMonitor {

  //TODO remove DRY
  final case class MetricNames(
    recoveryTime: String,
    recoveryTotal: String,
    persistentEvent: String,
    persistentEventTotal: String,
    snapshotTotal: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] {
    protected val defaultConfig: MetricNames =
      MetricNames(
        "akka_persistence_recovery_time",
        "akka_persistence_recovery_total",
        "akka_persistence_persistent_event",
        "akka_persistence_persistent_event_total",
        "akka_persistence_snapshot_total"
      )

    protected val mesmerConfig: String = "metrics.persistence-metrics"

    protected def extractFromConfig(config: Config): MetricNames = {
      val recoveryTime = config
        .tryValue("recovery-time")(_.getString)
        .getOrElse(defaultConfig.recoveryTime)
      val recoveryTotal = config
        .tryValue("recovery-total")(_.getString)
        .getOrElse(defaultConfig.recoveryTotal)
      val persistentEvent = config
        .tryValue("persistent-event")(_.getString)
        .getOrElse(defaultConfig.persistentEvent)
      val persistentEventTotal = config
        .tryValue("persistent-event-total")(_.getString)
        .getOrElse(defaultConfig.persistentEventTotal)
      val snapshotTotal = config
        .tryValue("snapshot")(_.getString)
        .getOrElse(defaultConfig.snapshotTotal)

      MetricNames(recoveryTime, recoveryTotal, persistentEvent, persistentEventTotal, snapshotTotal)

    }

  }
  def apply(meter: Meter, config: Config): OpenTelemetryPersistenceMetricsMonitor =
    new OpenTelemetryPersistenceMetricsMonitor(meter, MetricNames.fromConfig(config))
}

final class OpenTelemetryPersistenceMetricsMonitor(meter: Meter, metricNames: MetricNames) extends PersistenceMetricsMonitor {
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
