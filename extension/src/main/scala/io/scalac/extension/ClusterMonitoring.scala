package io.scalac.extension

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{
  ActorSystem,
  Extension,
  ExtensionId,
  SupervisorStrategy
}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import io.scalac.extension.config.ClusterMonitoringConfig

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

    system.systemActorOf(
      Behaviors
        .supervise(LocalSystemListener.apply(config.regions))
        .onFailure[Exception](SupervisorStrategy.restart),
      "localSystemMemberMonitor"
    )
  }

  def startReachabilityMonitor(): Unit = {

    log.info("Starting reachability monitor")

    ClusterSingleton(system)
      .init(
        SingletonActor(
          Behaviors
            .supervise(ListeningActor())
            .onFailure[Exception](SupervisorStrategy.restart),
          "MemberMonitoringActor"
        )
      )
  }
}
