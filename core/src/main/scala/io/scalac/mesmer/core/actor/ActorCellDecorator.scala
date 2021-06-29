package io.scalac.mesmer.core.actor

import akka.dispatch.{MailboxType, SingleConsumerOnlyUnboundedMailbox, UnboundedMailbox}
import io.scalac.mesmer.core.util.ReflectionFieldUtils

object ActorCellDecorator {

  final val fieldName = "_actorCellMetrics"

  private lazy val (getter, setter) = ReflectionFieldUtils.getHandlers("akka.actor.ActorCell", fieldName)

//  /**
//   * Initialize actor cell for usage by mesmer.
//   * Basically this means assigning meaningful values for fields added by mesmer.
//   * @param actorCell actor cell to be initialized
//   */
//  def initialize(actorCell: Object, mailboxType: MailboxType): Unit =
//    mailboxType match {
//      case _: UnboundedMailbox | _: SingleConsumerOnlyUnboundedMailbox =>
//        setter.invoke(actorCell, new ActorCellMetrics())
//      case _ => setter.invoke(actorCell, new ActorCellMetrics() with DroppedMessagesCellMetrics)
//    }

  //TODO this shouldn't fail when agent is not present - None should be returned
  def get(actorCell: Object): Option[ActorCellMetrics] =
    Option(getter.invoke(actorCell)).map(_.asInstanceOf[ActorCellMetrics])

}
