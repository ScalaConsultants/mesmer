package io.scalac.mesmer.core.model
import akka.{ actor => classic }

/**
 * case class aggregating information about actorRef used to unify models across several domains
 * @param ref
 *   classic actor ref
 * @param tags
 *   tags for respective actor ref
 */
private[scalac] final case class ActorRefDetails(ref: classic.ActorRef, tags: Set[Tag]) {
  def withTag(tag: Tag): ActorRefDetails = copy(tags = tags + tag)
}
