package io.scalac.core.util

import akka.actor.typed.ActorRef
import akka.{ actor => classic }

import io.scalac.core.model._

object ActorPathOps {

  def getPathString(actorRef: ActorRef[_]): ActorPath        = getPathString(actorRef.path)
  def getPathString(actorRef: classic.ActorRef): ActorPath   = getPathString(actorRef.path)
  def getPathString(actorPath: classic.ActorPath): ActorPath = actorPath.toActorPath

}
