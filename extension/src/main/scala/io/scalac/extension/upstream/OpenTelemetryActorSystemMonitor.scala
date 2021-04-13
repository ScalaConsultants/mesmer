package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.extension.config.Configuration
import io.scalac.extension.metric.ActorSystemMonitor
import io.scalac.extension.metric.ActorSystemMonitor.BoundMonitor
import io.scalac.extension.metric.RegisterRoot
import io.scalac.extension.upstream.OpenTelemetryActorSystemMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry.SynchronousInstrumentFactory
import io.scalac.extension.upstream.opentelemetry.WrappedCounter

object OpenTelemetryActorSystemMonitor {

  case class MetricNames(
    createdActors: String,
    terminatedActors: String
  )

  object MetricNames extends Configuration[MetricNames] {
    def default: MetricNames = MetricNames("created_actors_total", "terminated_actors_total")

    override protected val configurationBase: String = "io.scalac.akka-monitoring.metrics.actor-system-metrics"

    override protected def extractFromConfig(config: Config): MetricNames = {
      lazy val defaultCached = default
      val createdActors      = config.tryValue("created-actors")(_.getString).getOrElse(defaultCached.createdActors)

      val terminatedActors = config.tryValue("terminated-actors")(_.getString).getOrElse(defaultCached.createdActors)

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
