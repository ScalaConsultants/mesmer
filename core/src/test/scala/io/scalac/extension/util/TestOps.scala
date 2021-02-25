package io.scalac.extension.util

import akka.actor.typed.ActorRef
import org.scalatest.matchers.{ MatchResult, Matcher }

import java.util.UUID
import scala.util.Random

trait TestOps {

  def createUniqueId: String = UUID.randomUUID().toString

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
      .toSeq

  def sameOrParent(ref: ActorRef[_]): Matcher[ActorRef[_]] = left => {
    val test = ref.path == left.path || left.path.parent == ref.path

    MatchResult(test, s"${ref} is not same or parent of ${left}", s"${ref} is same as or parent of ${left}")
  }

}
