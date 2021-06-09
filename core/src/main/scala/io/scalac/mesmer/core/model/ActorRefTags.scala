package io.scalac.mesmer.core.model
import akka.{ actor => classic }

sealed trait ActorRefTags extends Any {
  def ref: classic.ActorRef
  def tags: Set[Tag]
}

object ActorRefTags {
  def apply(ref: classic.ActorRef): ActorRefTags                = ActorRefEmptyTags(ref)
  def apply(ref: classic.ActorRef, set: Set[Tag]): ActorRefTags = ActorRefNonEmptyTags(ref, set)

  private final case class ActorRefNonEmptyTags(ref: classic.ActorRef, tags: Set[Tag]) extends ActorRefTags
  private final case class ActorRefEmptyTags(ref: classic.ActorRef) extends ActorRefTags {
    def tags: Set[Tag] = Set.empty
  }

  def unapply(obj: ActorRefTags): Some[(classic.ActorRef, Set[Tag])] = Some(obj.ref, obj.tags)

}
