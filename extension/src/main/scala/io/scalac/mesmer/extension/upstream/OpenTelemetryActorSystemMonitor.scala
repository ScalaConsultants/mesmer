package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter
import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.extension.metric.ActorSystemMonitor.BoundMonitor
import io.scalac.mesmer.extension.metric.{ActorSystemMonitor, RegisterRoot}
import io.scalac.mesmer.extension.upstream.OpenTelemetryActorSystemMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry.{SynchronousInstrumentFactory, WrappedCounter}

object OpenTelemetryActorSystemMonitor {

  final case class MetricNames(
    createdActors: String,
    terminatedActors: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] {
    protected val defaultConfig: MetricNames =
      MetricNames("akka_system_created_actors_total", "akka_system_terminated_actors_total")

    protected val mesmerConfig: String = "metrics.actor-system-metrics"

    protected def extractFromConfig(config: Config): MetricNames = {
      val createdActors      = config.tryValue("created-actors")(_.getString).getOrElse(defaultConfig.createdActors)

      val terminatedActors = config.tryValue("terminated-actors")(_.getString).getOrElse(defaultConfig.createdActors)

      MetricNames(createdActors, terminatedActors)
    }
  }

  def apply(meter: Meter, config: Config): OpenTelemetryActorSystemMonitor =
    new OpenTelemetryActorSystemMonitor(meter, MetricNames.fromConfig(config))

}
final class OpenTelemetryActorSystemMonitor(val meter: Meter, metricNames: MetricNames) extends ActorSystemMonitor {

  private val createdActorsCounter = meter
    .longCounterBuilder(metricNames.createdActors)
    .setDescription("Amount of actors created measured from Actor System start")
    .build()

  private val terminatedActorsCounter = meter
    .longCounterBuilder(metricNames.terminatedActors)
    .setDescription("Amount of actors terminated measured from Actor System start")
    .build()

  override def bind(labels: ActorSystemMonitor.Labels): ActorSystemMonitor.BoundMonitor =
    new ActorSystemBoundMonitor(labels)

  class ActorSystemBoundMonitor(labels: ActorSystemMonitor.Labels)
      extends BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {
    private[this] val otLabels = LabelsFactory.of(labels.serialize)

    override lazy val createdActors: WrappedCounter = counter(createdActorsCounter, otLabels)(this)

    override lazy val terminatedActors: WrappedCounter = counter(terminatedActorsCounter, otLabels)(this)
  }
}
