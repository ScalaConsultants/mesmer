package io.scalac.core

import _root_.akka.actor.ActorPath
import _root_.akka.cluster.UniqueAddress
import _root_.akka.http.scaladsl.model.HttpMethod
import _root_.akka.http.scaladsl.model.Uri.{ Path => AkkaPath }
import io.scalac.core.model.Tag._
import io.scalac.core.tagging.{ @@, Tagged, _ }

package object model {

  type ShellInfo = (Array[StageInfo], Array[ConnectionStats])

  case class ConnectionStats(inName: StageName, outName: StageName, pull: Long, push: Long)

  case class StageInfo(stageName: StageName, subStreamName: SubStreamName)

  sealed trait NodeTag
  sealed trait PathTag
  sealed trait MethodTag
  sealed trait PersistenceIdTag

  type Node          = String @@ NodeTag
  type Path          = String @@ PathTag
  type Method        = String @@ MethodTag
  type PersistenceId = String @@ PersistenceIdTag
  type ActorKey      = Path
  type RawLabels     = Seq[(String, String)]

  implicit val nodeLabelSerializer: LabelSerializer[Node]   = node => Seq("node" -> node.asInstanceOf[String])
  implicit val pathLabelSerializer: LabelSerializer[Node]   = path => Seq("path" -> path.asInstanceOf[String])
  implicit val methodLabelSerializer: LabelSerializer[Node] = method => Seq("method" -> method.asInstanceOf[String])
  implicit val persistenceIdLabelSerializer: LabelSerializer[Node] = persistenceId =>
    Seq("persistenceId" -> persistenceId.asInstanceOf[String])

  sealed trait Direction {
    import Direction._
    def serialize: (String, String) = this match {
      case Out => ("direction", "out")
      case In  => ("direction", "in")
    }
  }

  object Direction {
    case object Out extends Direction
    case object In  extends Direction
  }

  implicit class AkkaNodeOps(val value: UniqueAddress) extends AnyVal {
    def toNode: Node = value.address.toString.taggedWith[NodeTag] // @todo change to some meaningful name
  }

  implicit class SerializeTagOps[T <: Tagged[T]](val optionalTag: Option[T]) extends AnyVal {
    def serialize(implicit ls: LabelSerializer[T]): RawLabels = optionalTag.fold[RawLabels](Seq.empty)(ls.serialize)
  }

  implicit class AkkaHttpPathOps(val path: AkkaPath) extends AnyVal {
    def toPath: Path = path.toString.taggedWith[PathTag]
  }

  implicit class AkkaHttpMethodOps(val method: HttpMethod) extends AnyVal {
    def toMethod: Method = method.value.taggedWith[MethodTag]
  }

  implicit class AkkaActorPathOps(val path: ActorPath) extends AnyVal {
    def toPath: Path = path.toStringWithoutAddress.taggedWith[PathTag]
  }

  trait LabelSerializer[T] extends (T => RawLabels) {
    final def apply(value: T): RawLabels = serialize(value)
    def serialize(value: T): RawLabels
  }

}
