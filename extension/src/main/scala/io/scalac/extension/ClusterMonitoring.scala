package io.scalac.extension

import akka.actor.typed.ActorSystem
import akka.actor.typed.Extension
import akka.cluster.typed.Cluster
import akka.cluster.typed.ClusterSingleton
import akka.cluster.typed.SingletonActor
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.ExtensionId

object ClusterMonitoring extends ExtensionId[ClusterMonitoring] {
  override def createExtension(system: ActorSystem[_]): ClusterMonitoring = new ClusterMonitoring(system).startMonitor()
}

class ClusterMonitoring(system: ActorSystem[_]) extends Extension {
  private def startMonitor(): ClusterMonitoring = {
    ClusterSingleton(system)
      .init(
        SingletonActor(
          Behaviors.supervise(ListeningActor()).onFailure[Exception](SupervisorStrategy.restart),
          "MemberMonitoringActor"
        )
      )
    this
  }
}
