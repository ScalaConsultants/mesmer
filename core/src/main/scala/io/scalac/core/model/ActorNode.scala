package io.scalac.core.model

import akka.actor.ActorPath

case class ActorNode(parent: ActorPath, child: ActorPath)
