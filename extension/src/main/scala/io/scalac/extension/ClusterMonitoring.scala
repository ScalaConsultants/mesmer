package io.scalac.extension

import akka.actor.ExtendedActorSystem
import akka.actor.typed.{ActorSystem, Extension, ExtensionId, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import io.opentelemetry.metrics.LongUpDownCounter

object ClusterMonitoring extends ExtensionId[ClusterMonitoring] {
  override def createExtension(system: ActorSystem[_]): ClusterMonitoring = {
    new ClusterMonitoring(system).startMonitor()
  }
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
