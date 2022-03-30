package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.Configuration
import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaPersistenceModule
import io.scalac.mesmer.extension.metric.Counter
import io.scalac.mesmer.extension.metric.Histogram
import io.scalac.mesmer.extension.metric.PersistenceMetricsMonitor
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.upstream.OpenTelemetryPersistenceMetricsMonitor._
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryPersistenceMetricsMonitor {

  // TODO remove DRY
  final case class MetricNames(
    recoveryTime: String,
    recoveryTotal: String,
    persistentEvent: String,
    persistentEventTotal: String,
    snapshotTotal: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] with Configuration {
    val defaultConfig: MetricNames =
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
  def apply(
    meter: Meter,
    moduleConfig: AkkaPersistenceModule.All[Boolean],
    config: Config
  ): OpenTelemetryPersistenceMetricsMonitor =
    new OpenTelemetryPersistenceMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))
}

final class OpenTelemetryPersistenceMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaPersistenceModule.All[Boolean],
  metricNames: MetricNames
) extends PersistenceMetricsMonitor {
  import PersistenceMetricsMonitor._

  private lazy val recoveryTimeRecorder = meter
    .histogramBuilder(metricNames.recoveryTime)
    .ofLongs()
    .setDescription("Amount of time needed for entity recovery")
    .build()

  private lazy val recoveryTotalCounter = meter
    .counterBuilder(metricNames.recoveryTotal)
    .setDescription("Amount of recoveries")
    .build()

  private lazy val persistentEventRecorder = meter
    .histogramBuilder(metricNames.persistentEvent)
    .ofLongs()
    .setDescription("Amount of time needed for entity to persist events")
    .build()

  private lazy val persistentEventTotalCounter = meter
    .counterBuilder(metricNames.persistentEventTotal)
    .setDescription("Amount of persist events")
    .build()

  private lazy val snapshotCounter = meter
    .counterBuilder(metricNames.snapshotTotal)
    .setDescription("Amount of snapshots created")
    .build()

  def bind(attributes: Attributes): BoundMonitor = new OpenTelemetryBoundMonitor(attributes)

  final class OpenTelemetryBoundMonitor(attributes: Attributes)
      extends BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {
    private val otAttributes = AttributesFactory.of(attributes.serialize)

    lazy val recoveryTime: Histogram[Long] =
      if (moduleConfig.recoveryTime) histogram(recoveryTimeRecorder, otAttributes)
      else noopHistogram

    lazy val persistentEvent: Histogram[Long] =
      if (moduleConfig.persistentEvent) histogram(persistentEventRecorder, otAttributes)
      else noopHistogram

    lazy val persistentEventTotal: Counter[Long] =
      if (moduleConfig.persistentEventTotal) counter(persistentEventTotalCounter, otAttributes)
      else noopCounter

    lazy val snapshot: Counter[Long] =
      if (moduleConfig.snapshot) counter(snapshotCounter, otAttributes) else noopCounter

    lazy val recoveryTotal: Counter[Long] =
      if (moduleConfig.recoveryTotal) counter(recoveryTotalCounter, otAttributes) else noopCounter

  }
}
