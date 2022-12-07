package io.scalac.mesmer.core.cluster

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorSystem
import akka.cluster.Cluster
import io.scalac.mesmer.core.model.{AkkaNodeOps, Node}
import org.slf4j.LoggerFactory

import scala.util.Try

object ClusterNode {
  private val log = LoggerFactory.getLogger(classOf[ClusterNode.type])

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

  private def reflectiveIsInstanceOf(fqcn: String, ref: Any): Either[String, Unit] =
    Try(Class.forName(fqcn)).toEither.left.map {
      case _: ClassNotFoundException => s"Class $fqcn not found"
      case e => e.getMessage
    }.filterOrElse(_.isInstance(ref), s"Ref $ref is not instance of $fqcn").map(_ => ())

}
