package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaActorSystemModule
import io.scalac.mesmer.extension.metric.ActorSystemMonitor
import io.scalac.mesmer.extension.metric.ActorSystemMonitor.BoundMonitor
import io.scalac.mesmer.extension.metric.Counter
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.upstream.OpenTelemetryActorSystemMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry.SynchronousInstrumentFactory

object OpenTelemetryActorSystemMonitor {

  final case class MetricNames(
    createdActors: String,
    terminatedActors: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] {
    val defaultConfig: MetricNames =
      MetricNames("akka_system_created_actors_total", "akka_system_terminated_actors_total")

    protected val mesmerConfig: String = "metrics.actor-system-metrics"

    protected def extractFromConfig(config: Config): MetricNames = {
      val createdActors = config.tryValue("created-actors")(_.getString).getOrElse(defaultConfig.createdActors)

      val terminatedActors = config.tryValue("terminated-actors")(_.getString).getOrElse(defaultConfig.createdActors)

      MetricNames(createdActors, terminatedActors)
    }
  }

  def apply(
    meter: Meter,
    moduleConfig: AkkaActorSystemModule.All[Boolean],
    config: Config
  ): OpenTelemetryActorSystemMonitor =
    new OpenTelemetryActorSystemMonitor(meter, moduleConfig, MetricNames.fromConfig(config))

}
final class OpenTelemetryActorSystemMonitor(
  val meter: Meter,
  moduleConfig: AkkaActorSystemModule.All[Boolean],
  metricNames: MetricNames
) extends ActorSystemMonitor {

  private lazy val createdActorsCounter = meter
    .counterBuilder(metricNames.createdActors)
    .setDescription("Amount of actors created measured from Actor System start")
    .build()

  private lazy val terminatedActorsCounter = meter
    .counterBuilder(metricNames.terminatedActors)
    .setDescription("Amount of actors terminated measured from Actor System start")
    .build()

  override def bind(labels: ActorSystemMonitor.Labels): ActorSystemMonitor.BoundMonitor =
    new ActorSystemBoundMonitor(labels)

  class ActorSystemBoundMonitor(labels: ActorSystemMonitor.Labels)
      extends BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {
    private[this] val otLabels = LabelsFactory.of(labels.serialize)

    lazy val createdActors: Counter[Long] =
      if (moduleConfig.createdActors) counter(createdActorsCounter, otLabels)(this) else noopCounter

    lazy val terminatedActors: Counter[Long] =
      if (moduleConfig.terminatedActors) counter(terminatedActorsCounter, otLabels)(this) else noopCounter
  }
}
