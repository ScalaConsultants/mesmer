package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.OpenTelemetry
import io.scalac.extension.metric.MetricRecorder.BoundWrappedValueRecorder
import io.scalac.extension.metric.{ PersistenceMetricMonitor, TrackingMetricRecorder }
import io.scalac.extension.upstream.OpenTelemetryPersistenceMetricMonitor._

import scala.collection.mutable.ListBuffer

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
        .tryValue("io.scalac.akka-cluster-monitoring.cluster-metrics")(
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
    extends PersistenceMetricMonitor { self =>
  import PersistenceMetricMonitor._
  private val recoveryTimeRecorder = OpenTelemetry
    .getGlobalMeter(instrumentationName)
    .longValueRecorderBuilder(metricNames.recoveryTime)
    .setDescription("Amount of time needed for entity recovery")
    .build()

  override type Bound = BoundMonitor

  override def transactionally[A, B, C <: self.type](
    one: TrackingMetricRecorder.Aux[A, C],
    two: TrackingMetricRecorder.Aux[B, C]
  ): Option[(A, B) => Unit] =
    (one.underlying, two.underlying) match {
      case (first: BoundWrappedValueRecorder, second: BoundWrappedValueRecorder) =>
        Some { (a, b) =>
          val labels: ListBuffer[String] = ListBuffer.empty
          first.labels.forEach {
            case (key, value) => labels ++= List(key, value)
          }
          OpenTelemetry
            .getGlobalMeter(instrumentationName)
            .newBatchRecorder(labels.toList: _*)
            .put(first.underlying, a.asInstanceOf[Long])
            .put(second.underlying, b.asInstanceOf[Long])
            .record()
        }
      case _ => None
    }

  override def bind(labels: Labels): BoundMonitor =
    new BoundMonitor {

      override lazy val recoveryTime: TrackingMetricRecorder.Aux[Long, self.type] =
        TrackingMetricRecorder.lift(new BoundWrappedValueRecorder(recoveryTimeRecorder, labels.toOpenTelemetry))
    }
}
