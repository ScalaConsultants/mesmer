package io.scalac.core.util

import akka.actor.typed.ActorRef
import akka.{ actor => classic }

object ActorPathOps {

  def getPathString(actorRef: ActorRef[_]): String        = getPathString(actorRef.path)
  def getPathString(actorRef: classic.ActorRef): String   = getPathString(actorRef.path)
  def getPathString(actorPath: classic.ActorPath): String = actorPath.toStringWithoutAddress

}
