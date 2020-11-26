package io.scalac.extension

import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Extension, ExtensionId, SupervisorStrategy }
import akka.cluster.typed.{ ClusterSingleton, SingletonActor }
import io.scalac.extension.config.ClusterMonitoringConfig
import io.scalac.extension.service.CommonRegexPathService
import io.scalac.extension.upstream.{
  NewRelicEventStream,
  OpenTelemetryClusterMetricsMonitor,
  OpenTelemetryHttpMetricsMonitor,
  OpenTelemetryPersistenceMetricMonitor
}

import scala.concurrent.duration._

object ClusterMonitoring extends ExtensionId[ClusterMonitoring] {
  override def createExtension(system: ActorSystem[_]): ClusterMonitoring = {
    val config  = ClusterMonitoringConfig.apply(system.settings.config)
    val monitor = new ClusterMonitoring(system, config)
    import config.boot._

    if (bootMemberEvent) {
      monitor.startMemberMonitor()
    }
    if (bootReachabilityEvents) {
      monitor.startReachabilityMonitor()
    }
    monitor.startAgentListener()
    monitor.startHttpEventListener()
    monitor
  }
}

class ClusterMonitoring(private val system: ActorSystem[_], val config: ClusterMonitoringConfig) extends Extension {

  private val instrumentationName = "scalac_akka_metrics"
  private val actorSystemConfig   = system.settings.config
  import system.log

  def startMemberMonitor(): Unit = {
    log.info("Starting member monitor")

    val openTelemetryClusterMetricsMonitor =
      OpenTelemetryClusterMetricsMonitor(
        instrumentationName,
        actorSystemConfig
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
          val newRelicEventStream    = new NewRelicEventStream(config)

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

  def startAgentListener(): Unit = {
    log.info("Starting local agent listener")

    val openTelemetryPersistenceMonitor = OpenTelemetryPersistenceMetricMonitor(instrumentationName, actorSystemConfig)

    system.systemActorOf(
      Behaviors
      // todo: configure persistence separately
        .supervise(PersistenceEventsListener.apply(openTelemetryPersistenceMonitor, config.regions.toSet))
        .onFailure[Exception](SupervisorStrategy.restart),
      "localAgentMonitor"
    )
  }

  def startHttpEventListener(): Unit = {
    log.info("Starting local http event listener")

    val openTelemetryHttpMonitor = OpenTelemetryHttpMetricsMonitor(instrumentationName, actorSystemConfig)
    val pathService              = CommonRegexPathService

    system.systemActorOf(
      Behaviors
        .supervise(HttpEventsActor.apply(openTelemetryHttpMonitor, pathService))
        .onFailure[Exception](SupervisorStrategy.restart),
      "httpEventMonitor"
    )
  }
}
