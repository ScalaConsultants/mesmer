package io.scalac.mesmer.extension.service

import akka.actor.typed._
import akka.{ actor => classic }

import io.scalac.mesmer.core.model.Tag
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.extension.util.Tree.Tree

object ActorTreeService {

  sealed trait Api extends Any

  sealed trait Command extends Api

  object Command {

    final case class GetActors(tags: Tag, reply: ActorRef[Seq[classic.ActorRef]]) extends Command

    final case class GetActorTree(reply: ActorRef[Tree[ActorRefDetails]]) extends Command

    final case class TagSubscribe(tag: Tag, reply: ActorRef[ActorRefDetails]) extends Command

  }

}
