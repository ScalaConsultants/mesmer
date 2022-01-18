package io.scalac.mesmer.core

import _root_.akka.actor.{ ActorPath => AkkaActorPath }
import _root_.akka.cluster.UniqueAddress
import _root_.akka.http.scaladsl.model.HttpMethod
import _root_.akka.http.scaladsl.model.Uri.{ Path => AkkaPath }

import scala.language.implicitConversions

import io.scalac.mesmer.core.model.stream.ConnectionStats
import io.scalac.mesmer.core.model.stream.StageInfo
import io.scalac.mesmer.core.tagging.@@
import io.scalac.mesmer.core.tagging._

package object model {

  type ShellInfo = (Array[StageInfo], Array[ConnectionStats])

  sealed trait ModelTag
  sealed trait NodeTag          extends ModelTag
  sealed trait InterfaceTag     extends ModelTag
  sealed trait PortTag          extends ModelTag
  sealed trait RegionTag        extends ModelTag
  sealed trait PathTag          extends ModelTag
  sealed trait StatusTag        extends ModelTag
  sealed trait MethodTag        extends ModelTag
  sealed trait PersistenceIdTag extends ModelTag
  sealed trait ActorPathTag     extends ModelTag

  type Node          = String @@ NodeTag
  type Interface     = String @@ InterfaceTag
  type Port          = Int @@ PortTag
  type Region        = String @@ RegionTag
  type Path          = String @@ PathTag
  type Status        = String @@ StatusTag
  type Method        = String @@ MethodTag
  type PersistenceId = String @@ PersistenceIdTag
  type ActorPath     = String @@ ActorPathTag
  type ActorKey      = ActorPath
  type RawAttributes = Seq[(String, String)]

  implicit val nodeAttributeSerializer: AttributeSerializer[Node] = node => Seq("node" -> node.unwrap)
  implicit val interfaceAttributeSerializer: AttributeSerializer[Interface] = interface =>
    Seq("interface" -> interface.unwrap)
  implicit val portAttributeSerializer: AttributeSerializer[Port]     = port => Seq("port" -> port.unwrap.toString)
  implicit val regionAttributeSerializer: AttributeSerializer[Region] = region => Seq("region" -> region.unwrap)
  implicit val pathAttributeSerializer: AttributeSerializer[Path]     = path => Seq("path" -> path.unwrap)
  implicit val statusAttributeSerializer: AttributeSerializer[Status] = status =>
    Seq("status" -> status.unwrap, "status_group" -> s"${status.charAt(0)}xx")

  implicit val methodAttributeSerializer: AttributeSerializer[Method] = method => Seq("method" -> method.unwrap)
  implicit val actorPathAttributeSerializer: AttributeSerializer[ActorPath] = actorPath =>
    Seq("actor_path" -> actorPath.unwrap)
  implicit val persistenceIdAttributeSerializer: AttributeSerializer[PersistenceId] = persistenceId =>
    Seq("persistence_id" -> persistenceId.asInstanceOf[String])

  /**
   * This function automatically wrap string values to a tagged type but this not perform any validation - careful
   * @param value
   * @return
   */
  implicit def stringAutomaticTagger[Tag <: ModelTag](value: String): String @@ Tag = value.taggedWith[Tag]
  implicit def intAutomaticTagger[Tag <: ModelTag](value: Int): Int @@ Tag          = value.taggedWith[Tag]

  implicit class AkkaNodeOps(private val value: UniqueAddress) extends AnyVal {
    def toNode: Node = value.address.toString.taggedWith[NodeTag] // @todo change to some meaningful name
  }

  implicit class AkkaHttpPathOps(private val path: AkkaPath) extends AnyVal {
    def toPath: Path = path.toString.taggedWith[PathTag]
  }

  implicit class AkkaHttpMethodOps(private val method: HttpMethod) extends AnyVal {
    def toMethod: Method = method.value.taggedWith[MethodTag]
  }

  implicit class AkkaActorPathOps(private val path: AkkaActorPath) extends AnyVal {
    def toPath: Path           = path.toStringWithoutAddress.taggedWith[PathTag]
    def toActorPath: ActorPath = path.toStringWithoutAddress.taggedWith[ActorPathTag]
  }

  implicit class SerializationOps[T](private val tag: T) extends AnyVal {
    def serialize(implicit ls: AttributeSerializer[T]): RawAttributes = ls.serialize(tag)
  }

  implicit class OptionSerializationOps[T](private val optTag: Option[T]) extends AnyVal {
    def serialize(implicit ls: AttributeSerializer[T]): RawAttributes =
      optTag.fold[RawAttributes](Seq.empty)(ls.serialize)
  }

}
