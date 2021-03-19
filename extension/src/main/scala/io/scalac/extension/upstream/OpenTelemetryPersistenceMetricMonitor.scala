package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.{ Counter, MetricRecorder, PersistenceMetricMonitor }
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

  private val recoveryTotalCounter = OpenTelemetry
    .getGlobalMeter(instrumentationName)
    .longCounterBuilder(metricNames.recoveryTotal)
    .setDescription("Amount of recoveries")
    .build()

  private val persistentEventRecorder = OpenTelemetry
    .getGlobalMeter(instrumentationName)
    .longValueRecorderBuilder(metricNames.persistentEvent)
    .setDescription("Amount of time needed for entity to persist events")
    .build()

  private val persistentEventTotalCounter = OpenTelemetry
    .getGlobalMeter(instrumentationName)
    .longCounterBuilder(metricNames.persistentEventTotal)
    .setDescription("Amount of persist events")
    .build()

  private val snapshotCounter = OpenTelemetry
    .getGlobalMeter(instrumentationName)
    .longCounterBuilder(metricNames.snapshotTotal)
    .setDescription("Amount of snapshots created")
    .build()

  override def bind(labels: Labels): BoundMonitor = new OpenTelemetryBoundMonitor(labels)

  class OpenTelemetryBoundMonitor(labels: Labels) extends BoundMonitor {
    private val openTelemetryLabels = LabelsFactory.of(labels.serialize)
    override lazy val recoveryTime  = WrappedLongValueRecorder(recoveryTimeRecorder, openTelemetryLabels)

    override lazy val persistentEvent: WrappedSynchronousInstrument[Long] with MetricRecorder[Long] =
      WrappedLongValueRecorder(persistentEventRecorder, openTelemetryLabels)

    override lazy val persistentEventTotal: WrappedSynchronousInstrument[Long] with Counter[Long] =
      WrappedCounter(persistentEventTotalCounter, openTelemetryLabels)

    override lazy val snapshot: WrappedSynchronousInstrument[Long] with Counter[Long] =
      WrappedCounter(snapshotCounter, openTelemetryLabels)

    override lazy val recoveryTotal: WrappedSynchronousInstrument[Long] with Counter[Long] =
      WrappedCounter(recoveryTotalCounter, openTelemetryLabels)

    override def unbind(): Unit = {
      recoveryTime.unbind()
      persistentEvent.unbind()
      persistentEventTotal.unbind()
      snapshot.unbind()
      recoveryTotal.unbind()
    }
  }
}
