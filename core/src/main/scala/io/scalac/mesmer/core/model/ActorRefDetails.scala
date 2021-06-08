package io.scalac.mesmer.core.model
import akka.{ actor => classic }

/**
 * case class aggregating information about actorRef used to unify models across several domains
 * @param ref classic actor ref
 * @param tags tags for respective actor ref
 * @param configuration mesmer configuration for specific actor ref
 */
private[scalac] final case class ActorRefDetails(
  ref: classic.ActorRef,
  tags: Set[Tag],
  configuration: ActorConfiguration
)
