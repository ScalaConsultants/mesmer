package io.scalac.core.util
import akka.actor.ActorRef
import io.scalac.core.model.Tag._

import scala.annotation.tailrec

object stream {

  //TODO FIX doc
  /**
   * Error prone way to get stream name from actor.
   * Most of the time actors that take part in running a stream has path following convention:
   * stream_name-{stream_id}-{island-id}-{last-operator-name}-{id}
   * @param ref
   * @return
   */
  def streamNameFromActorRef(ref: ActorRef): StreamName = {
    @tailrec
    def findName(materializerName: Option[String], offset: Int, name: String): String = materializerName match {
      case None =>
        val first   = name.indexOf('-', offset)
        val matName = name.substring(offset, first)
        findName(Some(matName), first + 1, name)
      case Some(matName) =>
        val next = name.indexOf('-', offset)
        val id   = name.substring(offset, next)
        s"$matName-$id"
    }
    val name = findName(None, 0, ref.path.name)
    StreamName(name)
  }
}
