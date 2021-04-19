package io.scalac.core.util

import akka.actor.typed.ActorRef
import org.scalatest.matchers.MatchResult
import org.scalatest.matchers.Matcher

import scala.util.Random

trait TestOps {

  def createUniqueId: String = java.util.UUID.randomUUID().toString

  def sameOrParent(parent: ActorRef[_]): Matcher[ActorRef[_]] = ref => {
    MatchResult(
      testSameOrParent(ref, parent),
      s"$parent is not same or parent of $ref",
      s"$parent is same as or parent of $ref"
    )
  }

  def randomString(length: Int): String = {
    val array: Array[Byte] = Array.fill(length)((Random.nextInt(26) + 97).toByte)
    new String(array)
  }

  /**
   * Create [[Seq]] and guarantees that it will contain unique strings
   * @param amount
   * @param length
   * @return
   */
  def generateUniqueString(amount: Int, length: Int): Seq[String] =
    LazyList
      .continually(randomString(length))
      .distinct
      .take(amount)
      .toList

  protected def testSameOrParent(ref: ActorRef[_], parent: ActorRef[_]): Boolean =
    parent.path == ref.path || ref.path.parent == parent.path

}
