package io.scalac.extension

import akka.actor.ExtendedActorSystem
import akka.actor.typed.{
  ActorSystem,
  Extension,
  ExtensionId,
  SupervisorStrategy
}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{Cluster, ClusterSingleton, SingletonActor}
import com.typesafe.config.Config
import io.opentelemetry.metrics.LongUpDownCounter

case class ClusterMonitoringConfig(boot: BootSettings, regions: List[String])

case class BootSettings(bootMemberEvent: Boolean,
                        bootReachabilityEvents: Boolean)

object ClusterMonitoringConfig {
  def apply(config: Config): ClusterMonitoringConfig =
    ClusterMonitoringConfig(BootSettings(true, true), Nil)
}

object ClusterMonitoring extends ExtensionId[ClusterMonitoring] {
  override def createExtension(system: ActorSystem[_]): ClusterMonitoring = {
    val monitor = new ClusterMonitoring(system)
//    monitor.startMonitor()
    monitor
  }
}

class ClusterMonitoring(private val system: ActorSystem[_]) extends Extension {

//  private lazy val cluster = Cluster(system)

  private val config = ClusterMonitoringConfig(system.settings.config)

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

  // initialization
  {
    if (config.boot.bootMemberEvent)
      startMemberMonitor()
    if (config.boot.bootMemberEvent)
      startReachabilityMonitor()
  }
}
