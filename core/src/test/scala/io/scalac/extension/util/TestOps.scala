package io.scalac.extension.util

import java.util.UUID

import akka.actor.typed.ActorRef

import org.scalatest.matchers.{ MatchResult, Matcher }

import io.scalac.core.util.ActorPathOps

trait TestOps {

  def createUniqueId: String = UUID.randomUUID().toString

  def sameOrParent(parent: ActorRef[_]): Matcher[ActorRef[_]] = ref => {
    MatchResult(
      testSameOrParent(ref, parent),
      s"${parent} is not same or parent of ${ref}",
      s"${parent} is same as or parent of ${ref}"
    )
  }

  protected def testSameOrParent(ref: ActorRef[_], parent: ActorRef[_]): Boolean =
    parent.path == ref.path || ref.path.parent == parent.path

}
