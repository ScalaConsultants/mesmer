package io.scalac.extension

import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{
  ActorSystem,
  Extension,
  ExtensionId,
  SupervisorStrategy
}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import io.scalac.extension.config.ClusterMonitoringConfig
import io.scalac.extension.upstream.{
  NewRelicEventStream,
  OpenTelemetryClusterMetricsMonitor
}

import scala.concurrent.duration._

object ClusterMonitoring extends ExtensionId[ClusterMonitoring] {
  override def createExtension(system: ActorSystem[_]): ClusterMonitoring = {
    val config = ClusterMonitoringConfig.apply(system.settings.config)
    val monitor = new ClusterMonitoring(system, config)
    import config.boot._

    if (bootMemberEvent) {
      monitor.startMemberMonitor()
    }
    if (bootReachabilityEvents) {
      monitor.startReachabilityMonitor()
    }
    monitor
  }
}

class ClusterMonitoring(private val system: ActorSystem[_],
                        val config: ClusterMonitoringConfig)
    extends Extension {

  import system.log

  def startMemberMonitor(): Unit = {
    log.info("Starting member monitor")

    val clusterMetricNames =
      OpenTelemetryClusterMetricsMonitor.MetricNames.fromConfig(
        system.settings.config
      )

    val openTelemetryClusterMetricsMonitor =
      new OpenTelemetryClusterMetricsMonitor(
        "scalac_akka_metrics",
        clusterMetricNames
      )

    system.systemActorOf(
      Behaviors
        .supervise(
          ClusterSelfNodeMetricGatherer
            .apply(
              openTelemetryClusterMetricsMonitor,
              config.regions,
              initTimeout = Some(60.seconds)
            )
        )
        .onFailure[Exception](SupervisorStrategy.restart),
      "localSystemMemberMonitor"
    )
  }

  def startReachabilityMonitor(): Unit = {

    log.info("Starting reachability monitor")

    NewRelicEventStream.NewRelicConfig
      .fromConfig(system.settings.config)
      .fold(
        errorMessage =>
          system.log
            .error(s"Couldn't start reachability monitor -> ${errorMessage}"),
        config => {
          implicit val classicSystem = system.classicSystem
          val newRelicEventStream = new NewRelicEventStream(config)

          ClusterSingleton(system)
            .init(
              SingletonActor(
                Behaviors
                  .supervise(ListeningActor(newRelicEventStream))
                  .onFailure[Exception](SupervisorStrategy.restart),
                "MemberMonitoringActor"
              )
            )

          CoordinatedShutdown(classicSystem).addTask(
            CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
            "closeNewRelicEventStream"
          )(() => newRelicEventStream.shutdown())
        }
      )
  }
}
