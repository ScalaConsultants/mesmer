package io.scalac.mesmer.core.util

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorSystem
import akka.cluster.Cluster
import org.slf4j.LoggerFactory

import io.scalac.mesmer.core.model.AkkaNodeOps
import io.scalac.mesmer.core.model.Node
import io.scalac.mesmer.core.util.ReflectionUtils.reflectiveIsInstanceOf

object TypedActorSystemOps {
  private val log = LoggerFactory.getLogger(classOf[TypedActorSystemOps.type])

  implicit class ActorSystemOps(system: ActorSystem[_]) {
    def clusterNodeName: Option[Node] =
      (for {
        _ <- reflectiveIsInstanceOf("akka.actor.typed.internal.adapter.ActorSystemAdapter", system)
        classic = system.classicSystem.asInstanceOf[ExtendedActorSystem]
        _ <- reflectiveIsInstanceOf("akka.cluster.ClusterActorRefProvider", classic.provider)
      } yield Cluster(classic).selfUniqueAddress).fold(
        message => {
          log.error(message)
          None
        },
        nodeName => Some(nodeName.toNode)
      )
  }
}
